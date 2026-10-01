package org.academy.api.common.entitycontrol;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MiningWorkPlanTest {
    @Test void dirtyBlockBehindCompletedScanPreventsCompletionAndIsDiscoveredFirst() {
        var plan = plan();
        plan.refresh(0, true, pos -> MiningWorkPlan.Eligibility.EXCLUDED);
        assertTrue(plan.completionVerified());
        plan.changed(BlockPos.ZERO);
        assertFalse(plan.completionVerified());
        var visits = new java.util.ArrayList<BlockPos>();
        plan.refresh(1, false, pos -> {
            visits.add(pos);
            return pos.equals(BlockPos.ZERO) ? MiningWorkPlan.Eligibility.ELIGIBLE : MiningWorkPlan.Eligibility.EXCLUDED;
        }, 1, () -> true);
        assertEquals(java.util.List.of(BlockPos.ZERO), visits);
        assertEquals(1, plan.progress().remaining());
        assertEquals(BlockPos.ZERO, plan.claim(UUID.randomUUID(), Vec3.ZERO, 1, ignored -> true));
        assertFalse(plan.completionVerified());
    }
    @Test void scanSlicesAreBoundedAndCannotAnnounceCompletionEarly() {
        var plan = new MiningWorkPlan(new BlockWorkRegion(Identifier.parse("minecraft:overworld"),
                BlockPos.ZERO, new BlockPos(15, 15, 15)));
        var visits = new java.util.concurrent.atomic.AtomicInteger();
        for (int tick = 0; tick < 64; tick++) {
            int before = visits.get();
            plan.refresh(tick, true, pos -> { visits.incrementAndGet(); return MiningWorkPlan.Eligibility.EXCLUDED; }, 64, () -> true);
            assertEquals(64, visits.get() - before);
            assertEquals(tick == 63, plan.completionVerified());
        }
        assertEquals(4096, visits.get());
    }
    @Test void massExternalRemovalUsesOnlyTheAllowedClassifications() {
        var plan = new MiningWorkPlan(new BlockWorkRegion(Identifier.parse("minecraft:overworld"),
                BlockPos.ZERO, new BlockPos(15, 15, 15)));
        plan.refresh(0, true, pos -> MiningWorkPlan.Eligibility.ELIGIBLE);
        for (int tick = 1; tick <= 64; tick++) {
            var allowed = new java.util.concurrent.atomic.AtomicInteger(64);
            plan.refresh(tick, true, pos -> MiningWorkPlan.Eligibility.EXCLUDED, 4096, () -> allowed.getAndDecrement() > 0);
            assertEquals(4096 - tick * 64, plan.progress().remaining());
        }
        assertEquals(4096, plan.progress().completed());
        assertTrue(plan.completionVerified());
    }
    @Test void manyBlockedUpperTargetsDoNotHideAReadyLowerLayer() {
        var plan = new MiningWorkPlan(new BlockWorkRegion(Identifier.parse("minecraft:overworld"),
                BlockPos.ZERO, new BlockPos(7, 1, 7)));
        plan.refresh(0, true, pos -> MiningWorkPlan.Eligibility.ELIGIBLE);
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) plan.block(new BlockPos(x, 1, z), "tool", 100);
        var target = plan.claim(UUID.randomUUID(), Vec3.ZERO, 1, pos -> true, 32, () -> true);
        assertNotNull(target);
        assertEquals(0, target.getY());
        assertEquals(64, plan.progress().blocked());
    }
    @Test void releasedWorkerCannotSubmitAnotherWorkersClaim() {
        var plan = plan();
        plan.refresh(0, true, pos -> MiningWorkPlan.Eligibility.ELIGIBLE);
        var first = UUID.randomUUID();
        var pos = plan.claim(first, Vec3.ZERO, 0, ignored -> true);
        plan.release(first);
        assertFalse(plan.owns(first, pos));
        var next = UUID.randomUUID();
        assertEquals(pos, plan.claim(next, Vec3.ZERO, 1, ignored -> true));
        assertTrue(plan.owns(next, pos));
        assertEquals(1, plan.progress().active());
    }
    private MiningWorkPlan plan() {
        return new MiningWorkPlan(new BlockWorkRegion(Identifier.parse("minecraft:overworld"),
                BlockPos.ZERO, new BlockPos(1, 2, 0)));
    }
    @Test void claimsUpperLayersAndKeepsOtherWorkersTargetsOutstanding() {
        var plan = plan();
        plan.refresh(0, true, pos -> MiningWorkPlan.Eligibility.ELIGIBLE);
        var first = plan.claim(UUID.randomUUID(), Vec3.ZERO, 0, pos -> true);
        var second = plan.claim(UUID.randomUUID(), Vec3.ZERO, 0, pos -> true);
        assertEquals(2, first.getY());
        assertEquals(2, second.getY());
        assertNotEquals(first, second);
        assertEquals(6, plan.progress().remaining());
        assertEquals(2, plan.progress().active());
    }
    @Test void blockedTargetsDoNotStarveOthersOrCountAsCompleted() {
        var plan = plan();
        plan.refresh(0, true, pos -> MiningWorkPlan.Eligibility.ELIGIBLE);
        var owner = UUID.randomUUID();
        var blocked = plan.claim(owner, Vec3.ZERO, 0, pos -> true);
        plan.block(blocked, "tool", 100);
        assertNotEquals(blocked, plan.claim(owner, Vec3.ZERO, 1, pos -> true));
        assertEquals(1, plan.progress().blocked());
        assertEquals(0, plan.progress().completed());
        assertEquals(6, plan.progress().remaining());
    }
    @Test void unloadedCellsPreventCompletionAndReleasedClaimsCanBeReassigned() {
        var plan = plan();
        plan.refresh(0, true, pos -> pos.equals(BlockPos.ZERO) ? MiningWorkPlan.Eligibility.UNLOADED : MiningWorkPlan.Eligibility.EXCLUDED);
        assertNull(plan.claim(UUID.randomUUID(), Vec3.ZERO, 0, pos -> true));
        assertEquals(1, plan.progress().remaining());
        plan.refresh(1, true, pos -> pos.equals(BlockPos.ZERO) ? MiningWorkPlan.Eligibility.ELIGIBLE : MiningWorkPlan.Eligibility.EXCLUDED);
        var owner = UUID.randomUUID();
        assertEquals(BlockPos.ZERO, plan.claim(owner, Vec3.ZERO, 1, pos -> true));
        plan.release(owner);
        assertEquals(BlockPos.ZERO, plan.claim(UUID.randomUUID(), Vec3.ZERO, 1, pos -> true));
        plan.resolve(BlockPos.ZERO);
        assertEquals(0, plan.progress().remaining());
        plan.refresh(2, true, pos -> pos.equals(BlockPos.ZERO) ? MiningWorkPlan.Eligibility.ELIGIBLE : MiningWorkPlan.Eligibility.EXCLUDED);
        assertEquals(1, plan.progress().remaining(), "A final scan must detect replacement blocks");
    }
}
