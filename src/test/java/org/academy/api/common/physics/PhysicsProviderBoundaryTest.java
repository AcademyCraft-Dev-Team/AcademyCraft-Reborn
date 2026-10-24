package org.academy.api.common.physics;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PhysicsProviderBoundaryTest {
    @Test
    void rejectedResultCannotContainAWorldOrSuccessfulPayload() {
        assertThrows(IllegalArgumentException.class, () -> new PhysicsResult<>(
                PhysicsResult.Status.UNSUPPORTED, Optional.of("partial state"), "unsupported"));
        assertThrows(IllegalArgumentException.class, () -> new PhysicsResult<>(
                PhysicsResult.Status.COMPLETED, Optional.empty(), ""));
        assertThrows(IllegalArgumentException.class,
                () -> PhysicsResult.rejected(PhysicsResult.Status.CAPACITY, ""));
        assertEquals(PhysicsResult.Done.INSTANCE, PhysicsResult.completed(PhysicsResult.Done.INSTANCE)
                .value().orElseThrow());
    }

    @Test
    void unwiredMetadataCannotAdvertiseExecutableCapabilities() {
        var identity = new PhysicsProvider.Identity("test:provider", "engine", "1", "revision", 1);
        assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.Descriptor(identity,
                PhysicsProvider.Readiness.SCAFFOLD_UNAVAILABLE, Set.of(PhysicsProvider.Capability.RIGID_BODY),
                Set.of(), "unimplemented"));
        var mutable = new java.util.HashSet<>(Set.of(PhysicsProvider.Capability.FLUID_SPH));
        var description = new PhysicsProvider.Descriptor(identity, PhysicsProvider.Readiness.SCAFFOLD_UNAVAILABLE,
                Set.of(), mutable, "unimplemented");
        mutable.clear();
        assertEquals(Set.of(PhysicsProvider.Capability.FLUID_SPH), description.planned());
        assertThrows(UnsupportedOperationException.class, () -> description.planned().clear());
    }

    @Test
    void atomicCommandRequestRejectsMixedEpochsAndWorldsBeforeDispatch() {
        var world = UUID.randomUUID();
        var reference = reference(world, 1, PhysicsObjectRef.Kind.RIGID);
        var valid = new PhysicsProvider.Impulse(reference, new Vec3(1, 0, 0), Vec3.ZERO);
        var commands = new ArrayList<PhysicsProvider.Command>(List.of(valid));
        var batch = new PhysicsProvider.CommandBatch(1, UUID.randomUUID(), commands);
        commands.clear();
        assertEquals(1, batch.commands().size());
        for (var invalid : List.of(reference(world, 2, PhysicsObjectRef.Kind.RIGID),
                reference(UUID.randomUUID(), 1, PhysicsObjectRef.Kind.RIGID))) {
            assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.CommandBatch(1,
                    UUID.randomUUID(), List.of(valid, new PhysicsProvider.Pause(invalid, true))));
        }
    }

    @Test
    void particleSnapshotsCannotMasqueradeAsRigidPosesOrMixTopologyCounts() {
        var fluid = reference(UUID.randomUUID(), 1, PhysicsObjectRef.Kind.FLUID);
        assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.RigidState(fluid,
                StructurePose.identity(Vec3.ZERO), Vec3.ZERO, Vec3.ZERO, false, false));
        assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.ParticleState(fluid,
                0, List.of(Vec3.ZERO), List.of(), false, false));
        assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.ParticleState(fluid,
                0, List.of(new Vec3(Double.NaN, 0, 0)), List.of(Vec3.ZERO), false, false));
        var paused = new PhysicsProvider.ParticleState(fluid, 0, List.of(Vec3.ZERO),
                List.of(Vec3.ZERO), false, true);
        assertTrue(paused.coveragePaused());
        assertFalse(paused.sleeping(), "Missing coverage is not ordinary particle sleep");
    }

    @Test
    void contactOverflowCannotBeReportedAsCompleteAndWorldsCannotCross() {
        var reference = reference(UUID.randomUUID(), 1, PhysicsObjectRef.Kind.RIGID);
        assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.ContactBatch(1, 3, true, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.Contact(1, 0, 0.5, true,
                reference, new PhysicsProvider.ObjectTarget(reference(UUID.randomUUID(), 1, PhysicsObjectRef.Kind.RIGID)),
                Vec3.ZERO, new Vec3(0, 1, 0), 10, 100));
        assertFalse(new PhysicsProvider.ContactBatch(1, 3, false, 1, List.of()).complete());
    }

    @Test
    void invalidTimeBudgetAndGenerationCannotEnterThePort() {
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, 0.10001}) {
            assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.StepRequest(1, 1, invalid, 2));
        }
        assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.Budget(
                1, 1, 0, 0, 0, 0, -1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PhysicsObjectRef("test:provider", UUID.randomUUID(),
                1, UUID.randomUUID(), 0, PhysicsObjectRef.Kind.RIGID));
    }

    @Test
    void createBatchRequiresUniqueLogicalObjectsAndImmutableExactGeometry() {
        var reference = reference(UUID.randomUUID(), 1, PhysicsObjectRef.Kind.RIGID);
        var boxes = new ArrayList<>(List.of(new PhysicsProvider.BoxShape(StructurePose.identity(Vec3.ZERO),
                new Vec3(0.5, 0.5, 0.5), new PhysicsProvider.ContactMaterial(0.6, 0))));
        var geometry = new PhysicsProvider.Geometry(new PhysicsProvider.GeometryKey(UUID.randomUUID(), 0), boxes);
        boxes.clear();
        assertEquals(1, geometry.boxes().size());
        var spec = new PhysicsProvider.RigidSpec(reference, StructurePose.identity(Vec3.ZERO), geometry,
                1, Vec3.ZERO, Vec3.ZERO, true);
        assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.CreateBatch(
                1, UUID.randomUUID(), List.of(spec, spec)));
        assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.CreateReceipt(
                1, UUID.randomUUID(), List.of(reference, reference)));
        assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.BoxShape(
                StructurePose.identity(Vec3.ZERO), new Vec3(0, 0.5, 0.5), new PhysicsProvider.ContactMaterial(0.6, 0)));
    }

    @Test
    void softAndFluidCreationDoNotAcceptUnresolvableTopologyOrPinnedFluidParticles() {
        var topology = new PhysicsProvider.GeometryKey(UUID.randomUUID(), 0);
        var particle = new PhysicsProvider.ParticleSeed(Vec3.ZERO, Vec3.ZERO, 1, false);
        var link = new PhysicsProvider.DistanceLink(0, 1, 1, 100, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.SoftSpec(
                reference(UUID.randomUUID(), 1, PhysicsObjectRef.Kind.SOFT), topology,
                PhysicsProvider.SoftSolver.XPBD, List.of(particle), List.of(link)));
        assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.FluidSpec(
                reference(UUID.randomUUID(), 1, PhysicsObjectRef.Kind.FLUID), topology,
                new PhysicsProvider.FluidMaterial(1000, 0.1, 0, 1, 1),
                List.of(new PhysicsProvider.ParticleSeed(Vec3.ZERO, Vec3.ZERO, 1, true))));
    }

    @Test
    void missingTerrainIsDistinctFromEmptyCoverageAndPauseIsExclusive() {
        var reference = reference(UUID.randomUUID(), 1, PhysicsObjectRef.Kind.RIGID);
        var assignment = new PhysicsProvider.CoverageAssignment(reference, Set.of("0:4:0"));
        var pending = new PhysicsProvider.TerrainPublication(1, UUID.randomUUID(), 0, 1,
                List.of(), List.of(assignment));
        assertTrue(pending.sections().isEmpty(), "Missing required sections must be handled by provider pause");
        assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.CoverageAssignment(reference, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.TerrainReceipt(1,
                UUID.randomUUID(), 1, Set.of(reference), Set.of(reference)));
        assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.TerrainPublication(1,
                UUID.randomUUID(), 4, 4, List.of(), List.of(assignment)));
    }

    @Test
    void overlapQueryCannotSilentlyTurnIntoAMovementSweep() {
        var at = StructurePose.identity(Vec3.ZERO);
        var geometry = new PhysicsProvider.Geometry(new PhysicsProvider.GeometryKey(UUID.randomUUID(), 0),
                List.of(new PhysicsProvider.BoxShape(at, new Vec3(0.5, 0.5, 0.5), new PhysicsProvider.ContactMaterial(0.6, 0))));
        assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.ShapeQuery(1,
                PhysicsProvider.QueryKind.OVERLAP, geometry, at, StructurePose.identity(new Vec3(0, 1, 0)),
                Set.of(PhysicsObjectRef.Kind.RIGID), true, List.of(), 1));
        var incomplete = new PhysicsProvider.QueryResult(1, 0, false, List.of());
        assertFalse(incomplete.complete(), "No returned hits is not a verified clear path when incomplete");
    }

    @Test
    void terrainQueriesAndCreationReceiptsCannotMixWorldsSharingTheSameEpoch() {
        var world = UUID.randomUUID();
        var first = reference(world, 1, PhysicsObjectRef.Kind.RIGID);
        var at = StructurePose.identity(Vec3.ZERO);
        var geometry = new PhysicsProvider.Geometry(new PhysicsProvider.GeometryKey(UUID.randomUUID(), 0),
                List.of(new PhysicsProvider.BoxShape(at, new Vec3(0.5, 0.5, 0.5),
                        new PhysicsProvider.ContactMaterial(0.6, 0))));
        var foreignWorld = reference(UUID.randomUUID(), 1, PhysicsObjectRef.Kind.RIGID);
        var foreignProvider = new PhysicsObjectRef("test:other_provider", world, 1, UUID.randomUUID(),
                1, PhysicsObjectRef.Kind.RIGID);
        for (var foreign : List.of(foreignWorld, foreignProvider)) {
            assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.CreateReceipt(
                    1, UUID.randomUUID(), List.of(first, foreign)));
            assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.ShapeQuery(
                    1, PhysicsProvider.QueryKind.OVERLAP, geometry, at, at,
                    Set.of(PhysicsObjectRef.Kind.RIGID), true, List.of(first, foreign), 4));
            assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.TerrainPublication(
                    1, UUID.randomUUID(), 0, 1, List.of(), List.of(
                            new PhysicsProvider.CoverageAssignment(first, Set.of("0:4:0")),
                            new PhysicsProvider.CoverageAssignment(foreign, Set.of("0:4:0")))));
            assertThrows(IllegalArgumentException.class, () -> new PhysicsProvider.TerrainReceipt(
                    1, UUID.randomUUID(), 1, Set.of(first), Set.of(foreign)));
        }
    }

    private static PhysicsObjectRef reference(UUID world, long epoch, PhysicsObjectRef.Kind kind) {
        return new PhysicsObjectRef("test:provider", world, epoch, UUID.randomUUID(), 1, kind);
    }
}
