package org.academy.api.common.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.upgrade.StructureOrientation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProgramStructureOrientationRuntimeTest {
    @Test
    void unsupportedImplementationsCannotClaimCompletion() {
        var runtime = new ProgramStructureOrientationRuntime() { };
        var control = new ProgramStructureControl(new StructureRef(UUID.randomUUID(),
                Identifier.parse("minecraft:overworld"), Identifier.parse("academy:test"), 1),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertEquals(ProgramStructureOperationResult.Status.UNAVAILABLE,
                runtime.rotateOrientation(control, null).result().status());
        assertEquals(ProgramStructureOperationResult.Status.UNAVAILABLE,
                runtime.resetOrientation(control, reset(100)).result().status());
        assertEquals(ProgramStructureOperationResult.Status.UNAVAILABLE,
                runtime.spinStructure(control, spin(-2.0, 20)).result().status());
        assertEquals(ProgramStructureOperationResult.Status.UNAVAILABLE,
                runtime.orientationStatus(control, UUID.randomUUID()).result().status());
    }

    @Test
    void pendingRequiresAReceiptAndBlockedIsNotCompletion() {
        assertThrows(IllegalArgumentException.class, () -> new ProgramStructureOrientationRuntime.Operation(
                ProgramStructureOperationResult.pending("preparing"), null));
        var id = UUID.randomUUID();
        assertEquals(id, ProgramStructureOrientationRuntime.Operation.pending(id, "preparing").operationId());
        var blocked = ProgramStructureOrientationRuntime.Operation.blocked("wall");
        assertEquals(ProgramStructureOperationResult.Status.REJECTED, blocked.result().status());
        assertEquals("orientation_blocked:wall", blocked.result().detail());
        assertNull(blocked.operationId());
        assertEquals(ProgramStructureOperationResult.Status.REJECTED,
                ProgramStructureOrientationRuntime.Operation.fromLegacy(
                        ProgramStructureOperationResult.pending("untrackable")).result().status());
        var synchronous = ProgramStructureOperationResult.completed("extension_completed");
        assertEquals(synchronous, ProgramStructureOrientationRuntime.Operation.fromLegacy(synchronous).result());
    }

    @Test
    void finiteSignedSpeedAndBoundedLifetimeAreEnforcedAtTheApiBoundary() {
        assertEquals(-2.0, spin(-2.0, 20).angularSpeed());
        for (double speed : new double[]{0.0, Double.NaN, Double.POSITIVE_INFINITY, 100.01, -100.01}) {
            assertThrows(IllegalArgumentException.class, () -> spin(speed, 20));
        }
        for (int ticks : new int[]{0, -1, 1_201}) {
            assertThrows(IllegalArgumentException.class, () -> spin(1.0, ticks));
            assertThrows(IllegalArgumentException.class, () -> reset(ticks));
        }
        assertEquals(1_200, reset(1_200).timeoutTicks());
        assertEquals(-1.0, new ProgramStructureOrientationRuntime.SpinRequest(
                new ProgramDirection(0, -1, 0), StructureOrientation.AxisFrame.WORLD,
                1.0, 20, StructureOrientation.StopMode.BRAKE).axis().y());
    }

    private static ProgramStructureOrientationRuntime.ResetRequest reset(int ticks) {
        return new ProgramStructureOrientationRuntime.ResetRequest(StructureOrientation.ResetMode.CAPTURE_BASELINE,
                StructureOrientation.MissingBaselinePolicy.REJECT, ticks);
    }

    private static ProgramStructureOrientationRuntime.SpinRequest spin(double speed, int ticks) {
        return new ProgramStructureOrientationRuntime.SpinRequest(new ProgramDirection(0, 1, 0),
                StructureOrientation.AxisFrame.LOCAL, speed, ticks, StructureOrientation.StopMode.BRAKE);
    }
}
