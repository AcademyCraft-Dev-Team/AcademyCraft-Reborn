package org.academy.api.common.entitycontrol;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MiningWorkPlanTest {
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
