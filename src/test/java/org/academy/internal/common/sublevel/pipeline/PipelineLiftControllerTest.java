package org.academy.internal.common.sublevel.pipeline;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureDynamics;
import org.academy.api.common.structure.StructurePipeline;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PipelineLiftControllerTest {
    private final PipelineLiftController controller = new PipelineLiftController();
    private static final StructureRef REF = new StructureRef(UUID.randomUUID(),
            Identifier.fromNamespaceAndPath("minecraft", "overworld"),
            Identifier.fromNamespaceAndPath("academy", "sublevel"), 1);
    private static final UUID RUNTIME = UUID.randomUUID();
    private static final StructurePipeline.Epoch EPOCH = new StructurePipeline.Epoch(REF, RUNTIME, 1);
    private static final Vec3 SOURCE = new Vec3(100, 64, -30);

    private static StructurePipeline.LiftRequest request(double rise, long deadline) {
        return new StructurePipeline.LiftRequest(EPOCH, SOURCE, rise, 200, deadline);
    }

    private static StructureDynamics.State at(double dy, double dx, double dz) {
        return new StructureDynamics.State(StructurePose.identity(SOURCE.add(dx, dy, dz)),
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 10.0);
    }

    @Test
    void tierArrivalLatchesAndStartsTheHoverClock() {
        var state = controller.advance(request(3, 1_000), null, at(3.0, 0, 0), 100);
        assertEquals(StructurePipeline.LiftPhase.HOVERING, state.phase());
        assertEquals(100, state.arrivalTick());
        assertEquals(300, state.hoverDeadlineTick());

        var later = controller.advance(request(3, 1_000), state, at(3.0, 0, 0), 150);
        assertEquals(100, later.arrivalTick(), "arrival must not be refreshed");
        assertEquals(300, later.hoverDeadlineTick(), "hover deadline must not move");
    }

    @Test
    void threeFiveAndSevenAreTheOnlyRises() {
        for (double rise : new double[]{3, 5, 7}) {
            var state = controller.advance(request(rise, 10_000), null, at(rise - 0.05, 0, 0), 10);
            assertEquals(StructurePipeline.LiftPhase.HOVERING, state.phase());
        }
        assertThrows(IllegalArgumentException.class, () -> controller.advance(request(4, 100), null, at(4, 0, 0), 1));
    }

    @Test
    void liftingBeforeArrivalAndLiftDeadline() {
        var state = controller.advance(request(5, 50), null, at(2.0, 0, 0), 10);
        assertEquals(StructurePipeline.LiftPhase.LIFTING, state.phase());
        assertEquals(-1, state.arrivalTick());

        var blocked = controller.advance(request(5, 50), state, at(2.0, 0, 0), 51);
        assertEquals(StructurePipeline.LiftPhase.BLOCKED, blocked.phase());
        assertEquals("lift_deadline", blocked.detail());
    }

    @Test
    void hoverExpiresExactlyAfterTheLatchedDeadline() {
        var state = controller.advance(request(3, 1_000), null, at(3, 0, 0), 100);
        var valid = controller.advance(request(3, 1_000), state, at(3, 0, 0), 300);
        assertEquals(StructurePipeline.LiftPhase.HOVERING, valid.phase());
        var expired = controller.advance(request(3, 1_000), state, at(3, 0, 0), 301);
        assertEquals(StructurePipeline.LiftPhase.EXPIRED, expired.phase());
        assertEquals(100, expired.arrivalTick());
        assertEquals(300, expired.hoverDeadlineTick());
    }

    @Test
    void horizontalDriftPreventsArrival() {
        var state = controller.advance(request(3, 1_000), null, at(3.0, 0.2, 0), 100);
        assertEquals(StructurePipeline.LiftPhase.LIFTING, state.phase());
    }

    @Test
    void terminalPhaseIsPreserved() {
        var launched = new StructurePipeline.LiftState(EPOCH, StructurePipeline.LiftPhase.LAUNCHED,
                100, 300, "launched");
        assertSame(launched, controller.advance(request(3, 1_000), launched, at(3, 0, 0), 999));
    }

    @Test
    void aNewEpochRestartsTheLift() {
        var old = controller.advance(request(3, 100), null, at(3, 0, 0), 10);
        var newEpoch = new StructurePipeline.Epoch(REF, RUNTIME, 2);
        var request = new StructurePipeline.LiftRequest(newEpoch, SOURCE, 3, 200, 500);
        var restarted = controller.advance(request, old, at(1.0, 0, 0), 20);
        assertEquals(StructurePipeline.LiftPhase.LIFTING, restarted.phase());
        assertEquals(newEpoch, restarted.epoch());
    }

    @Test
    void invalidRequestsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.advance(new StructurePipeline.LiftRequest(EPOCH, SOURCE, 3, -1, 100), null,
                        at(0, 0, 0), 1));
        assertThrows(IllegalArgumentException.class,
                () -> controller.advance(request(3, 100), null, null, 1));
        assertThrows(IllegalArgumentException.class,
                () -> controller.advance(request(3, -1), null, at(0, 0, 0), 1));
    }
}
