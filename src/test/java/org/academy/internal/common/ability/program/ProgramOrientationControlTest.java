package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramStructureOperationResult;
import org.academy.api.common.ability.program.ProgramStructureOrientationRuntime;
import org.academy.api.common.structure.upgrade.StructureOperation;
import org.academy.api.common.structure.upgrade.StructureOrientation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProgramOrientationControlTest {
    @Test
    void serverRejectsBroadApiSpeedsAndPreservesFiniteBrakingTimeWithoutClampingIntent() {
        assertNull(ProgramOrientationControl.spinRejection(spin(-2.0, 1_160)));
        assertEquals("orientation_speed_exceeds_server_limit", ProgramOrientationControl.spinRejection(spin(2.01, 20)));
        assertEquals("orientation_speed_exceeds_server_limit", ProgramOrientationControl.spinRejection(spin(-100.0, 20)));
        assertEquals("orientation_duration_leaves_no_braking_budget", ProgramOrientationControl.spinRejection(spin(1.0, 1_161)));
        assertEquals("orientation_duration_leaves_no_braking_budget", ProgramOrientationControl.spinRejection(spin(1.0, 1_200)));
        var limits = ProgramOrientationControl.limits(100);
        assertEquals(2.0, limits.maxAngularSpeed());
        assertEquals(0.0, limits.maxRetreatDistance(), "program rotation cannot authorize a translation retreat");
        assertThrows(IllegalArgumentException.class, () -> ProgramOrientationControl.limits(1_201));
    }

    @Test
    void backendPreviewAndUntrackedTerrainNeverProduceASuccessOrUnboundedRetry() {
        for (var status : StructureOperation.Status.values()) {
            var id = status == StructureOperation.Status.RUNNING ? Optional.of(UUID.randomUUID()) : Optional.<UUID>empty();
            var result = ProgramOrientationControl.operation(new StructureOperation.Result<>(status, "test", id, Optional.empty()));
            switch (status) {
                case RUNNING -> {
                    assertEquals(ProgramStructureOperationResult.Status.PENDING, result.result().status());
                    assertEquals(id.orElseThrow(), result.operationId());
                }
                case COMPLETED -> assertEquals(ProgramStructureOperationResult.Status.COMPLETED, result.result().status());
                case UNSUPPORTED -> assertEquals(ProgramStructureOperationResult.Status.UNAVAILABLE, result.result().status());
                default -> assertEquals(ProgramStructureOperationResult.Status.REJECTED, result.result().status(), status.name());
            }
        }
        var id = UUID.randomUUID();
        var terrain = ProgramOrientationControl.operation(new StructureOperation.Result<>(
                StructureOperation.Status.TERRAIN_PENDING, "terrain_pending", Optional.of(id), Optional.empty()));
        assertEquals(id, terrain.operationId());
        assertEquals(ProgramStructureOperationResult.Status.PENDING, terrain.result().status());
    }

    @Test
    void sessionTerminationReleasesOnlyTheFramesOrientationRuntime() {
        var closed = new int[1];
        var runtime = new ProgramStructureOrientationRuntime() {
            @Override public void closeOrientationCommands() { closed[0]++; }
        };
        ServerProgramExecution.closeOrientationCommands(new ProgramExecutionFrame(new ProgramActionTransaction(), runtime));
        assertEquals(1, closed[0]);
        ServerProgramExecution.closeOrientationCommands(new ProgramExecutionFrame(new ProgramActionTransaction(), new Object()));
        assertEquals(1, closed[0], "other ability runtimes have no angular cleanup to invoke");
    }

    private static ProgramStructureOrientationRuntime.SpinRequest spin(double speed, int ticks) {
        return new ProgramStructureOrientationRuntime.SpinRequest(new ProgramDirection(0, 1, 0),
                StructureOrientation.AxisFrame.WORLD, speed, ticks, StructureOrientation.StopMode.BRAKE);
    }
}
