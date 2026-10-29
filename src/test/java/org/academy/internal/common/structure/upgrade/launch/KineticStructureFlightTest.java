package org.academy.internal.common.structure.upgrade.launch;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureTeleportedEvent;
import org.academy.api.common.structure.upgrade.StructureDamage;
import org.academy.api.common.structure.upgrade.StructurePathDamage;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class KineticStructureFlightTest {
    @Test void sweepSourceWitnessUsesOnlyHandleIdentityPoseAndRevision() {
        var reference = new StructureRef(UUID.randomUUID(),Identifier.parse("minecraft:overworld"),Identifier.parse("academy:sublevel"),1);
        class Handle implements org.academy.api.common.structure.StructureHandle {
            long revision = 7; UUID runtime = UUID.randomUUID(); StructurePose currentPose = StructurePose.identity(Vec3.ZERO);
            public StructureRef reference() { return reference; }
            public org.academy.api.common.structure.StructureLifecycle lifecycle() { return org.academy.api.common.structure.StructureLifecycle.ACTIVE; }
            public UUID runtimeId() { return runtime; }
            public long geometryRevision() { return revision; }
            public StructurePose pose() { return currentPose; }
            public void setPose(StructurePose value) { currentPose=value; }
            public org.academy.api.common.structure.StructureWorldAccess world() { throw new AssertionError("witness must not read body contents or rebuild shapes"); }
        }
        var handle = new Handle();
        var witness = new StructurePathDamage.SourceWitness(reference,handle.runtime,7,handle.currentPose);
        assertTrue(witness.matches(handle));
        handle.revision++; assertFalse(witness.matches(handle)); handle.revision--;
        handle.setPose(StructurePose.identity(new Vec3(0,0,20))); assertFalse(witness.matches(handle));
        handle.setPose(witness.pose()); handle.runtime=UUID.randomUUID(); assertFalse(witness.matches(handle));
        assertTrue(new StructurePathDamage.Sweep(List.of(new AABB(0,0,0,1,1,1)),witness.pose(),witness.pose()).source().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new StructurePathDamage.SourceWitness(reference,UUID.randomUUID(),-1,witness.pose()));
    }
    @Test void contactWitnessFindsTheFirstOccupiedFaceAndRejectsAnEmptyGap() {
        var boxes = List.of(new AABB(0,0,0,1,1,1));
        var from = StructurePose.identity(Vec3.ZERO); var to = StructurePose.identity(new Vec3(4,0,0));
        assertEquals(.25, KineticStructureFlight.firstContactFraction(boxes, from, to,
                new AABB(2,0,0,3,1,1)), 1E-4);
        assertFalse(Double.isFinite(KineticStructureFlight.firstContactFraction(boxes, from, to,
                new AABB(2,0,2,3,1,3))));
    }
    @Test void instantRelocationOnlyRevokesTheMatchingStableBodyActivation() {
        var source = new StructureRef(UUID.randomUUID(), Identifier.parse("minecraft:overworld"), Identifier.parse("academy:sublevel"), 3);
        var runtime = UUID.randomUUID(); var pose = StructurePose.identity(Vec3.ZERO);
        var stable = new StructureRef(source.id(), source.dimension(), source.backend(), 0);
        assertTrue(KineticStructureFlight.relocationMatches(source, runtime, new StructureTeleportedEvent.Relocation(stable, runtime, pose, pose)));
        assertFalse(KineticStructureFlight.relocationMatches(source, runtime, new StructureTeleportedEvent.Relocation(stable, UUID.randomUUID(), pose, pose)));
        var foreign = new StructureRef(source.id(), source.dimension(), Identifier.parse("academy:sable"), 0);
        assertFalse(KineticStructureFlight.relocationMatches(source, runtime, new StructureTeleportedEvent.Relocation(foreign, runtime, pose, pose)));
    }
    @Test void capacityIsAdmittedBeforeMotionAndSettlementRecordsDoNotBecomeExtraFlyingBodies() {
        assertTrue(KineticStructureFlight.quotaAllows(63, 15, 3));
        assertFalse(KineticStructureFlight.quotaAllows(64, 0, 0));
        assertFalse(KineticStructureFlight.quotaAllows(16, 16, 0));
        assertFalse(KineticStructureFlight.quotaAllows(4, 4, 4));
        assertFalse(KineticStructureFlight.quotaAllows(-1, 0, 0));
    }
    @Test void sourceSelectionIsStableAcrossActivationsAndDoesNotExcludeOtherDimensionsOrBackends() {
        var source = new StructureRef(UUID.randomUUID(), Identifier.parse("minecraft:overworld"), Identifier.parse("academy:sublevel"), 1);
        var reloaded = new StructureRef(source.id(), source.dimension(), source.backend(), 99);
        var foreignBackend = new StructureRef(source.id(), source.dimension(), Identifier.parse("academy:sable"), 1);
        var foreignDimension = new StructureRef(source.id(), Identifier.parse("minecraft:the_nether"), source.backend(), 1);
        var excluded = StructureDamage.Targets.excluding(source);
        assertFalse(excluded.includes(source)); assertFalse(excluded.includes(reloaded));
        assertTrue(excluded.includes(foreignBackend)); assertTrue(excluded.includes(foreignDimension));
        assertTrue(excluded.host());
        var fragments = StructureDamage.Targets.fragments(source);
        assertTrue(fragments.includes(reloaded)); assertFalse(fragments.includes(foreignBackend));
        assertFalse(fragments.host()); assertTrue(fragments.descendants());
    }

    @Test void radiusUsesTheActualLocalCenterAndCapsSweepGeometry() {
        assertEquals(Math.sqrt(0.75), KineticStructureFlight.radius(List.of(new AABB(10, 20, 30, 11, 21, 31)),
                new Vec3(10.5, 20.5, 30.5)), 1E-9);
        var pose = StructurePose.identity(Vec3.ZERO);
        var boxes = new ArrayList<AABB>(); boxes.add(new AABB(0, 0, 0, 1, 1, 1));
        var sweep = new StructurePathDamage.Sweep(boxes, pose, pose);
        boxes.clear(); assertEquals(1, sweep.localBoxes().size());
        assertThrows(IllegalArgumentException.class, () -> new StructurePathDamage.Sweep(List.of(), pose, pose));
        assertThrows(IllegalArgumentException.class, () -> new StructurePathDamage.Sweep(Collections.nCopies(129,
                new AABB(0, 0, 0, 1, 1, 1)), pose, pose));
    }
}
