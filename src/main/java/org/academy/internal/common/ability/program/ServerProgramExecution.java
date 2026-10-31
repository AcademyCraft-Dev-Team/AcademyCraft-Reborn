package org.academy.internal.common.ability.program;

import org.academy.internal.common.ability.program.compile.CompiledProgram;
import org.academy.internal.common.ability.program.registry.ProgramExecutorLookup;


import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.ProgramStructureOrientationRuntime;
import org.academy.api.common.ability.program.ProgramRunStatus;

import java.util.Optional;

/** Shared transactional execution path for immediate and resumable server programs. */
public final class ServerProgramExecution {
    public static final long MAX_LIFETIME_TICKS = 72_000L;

    private ServerProgramExecution() {
    }

    public static Result execute(
            CompiledProgram program,
            ServerPlayer player,
            Identifier category,
            int fuelPerTick,
            ProgramExecutorLookup executors,
            ProgramExecutionFrame frame,
            ProgramActionTransaction transaction,
            ProgramInvocationContext invocation
    ) {
        boolean scheduled = false;
        try {
            var session = new ProgramVm.Session(program);
            var now = player.level().getGameTime();
            var vmResult = session.run(now, fuelPerTick, executors, frame);
            if (vmResult.status() == ProgramVmResult.Status.COMPLETED) {
                var committed = transaction.commit();
                if (committed.successful()) transaction.release();
                ProgramRunObservations.settled(invocation, committed.successful() ? ProgramRunStatus.Phase.COMPLETED : ProgramRunStatus.Phase.FAILED,
                        committed.successful() ? vmResult.nodeId() : committed.nodeId(), committed.successful() ? ProgramVmDiagnostic.NONE
                                : AbilityProgramManager.actionDiagnostic(committed.cause()), null, committed.successful() ? "committed" : "commit_failed",
                        committed.rollbackFailures() > 0, player.level().getGameTime());
                return new Result(vmResult, Optional.of(committed));
            }
            if (vmResult.status() == ProgramVmResult.Status.FAILED) {
                var aborted = transaction.abort();
                ProgramRunObservations.settled(invocation, ProgramRunStatus.Phase.FAILED, vmResult.nodeId(), vmResult.diagnostic(),
                        vmResult.port(), vmResult.detail(), !aborted.successful(), player.level().getGameTime());
                return new Result(vmResult, aborted.successful()
                        ? Optional.empty() : Optional.of(aborted));
            }
            if (invocation == null) {
                transaction.abort();
                return rejected(vmResult);
            }

            var key = new ServerProgramScheduler.SessionKey(
                    player.getUUID(), category, invocation.programId(), invocation.slot());
            scheduled = ServerProgramScheduler.resume(
                    player.level().getServer(), key, session, executors, frame, fuelPerTick,
                    now + 1L, MAX_LIFETIME_TICKS,
                    (_, termination) -> {
                        try {
                            finishDeferred(player, category, invocation, transaction, termination);
                        } finally {
                            closeOrientationCommands(frame);
                        }
                    }
            );
            if (!scheduled) {
                var aborted = transaction.abort();
                ProgramRunObservations.settled(invocation, ProgramRunStatus.Phase.FAILED, vmResult.nodeId(), ProgramVmDiagnostic.ACTION_REJECTED,
                        null, "session_admission_rejected", !aborted.successful(), player.level().getGameTime());
                return rejected(vmResult);
            }
            return new Result(vmResult, Optional.empty());
        } finally {
            if (!scheduled) closeOrientationCommands(frame);
        }
    }

    static void closeOrientationCommands(ProgramExecutionFrame frame) {
        try {
            frame.environment(ProgramStructureOrientationRuntime.class)
                    .ifPresent(ProgramStructureOrientationRuntime::closeOrientationCommands);
        } catch (RuntimeException exception) {
            AcademyCraft.LOGGER.warn("Failed to release program orientation commands", exception);
        }
        try {
            frame.environment(org.academy.api.common.ability.program.ProgramStructureControlRuntime.class)
                    .ifPresent(org.academy.api.common.ability.program.ProgramStructureControlRuntime::closeStructureControls);
        } catch (RuntimeException exception) {
            AcademyCraft.LOGGER.warn("Failed to release program single-structure controls", exception);
        }
        try {
            frame.environment(org.academy.api.common.ability.program.ProgramStructureGroupRuntime.class)
                    .ifPresent(org.academy.api.common.ability.program.ProgramStructureGroupRuntime::closeStructureGroups);
        } catch (RuntimeException exception) {
            AcademyCraft.LOGGER.warn("Failed to release program structure groups", exception);
        }
    }

    private static Result rejected(ProgramVmResult vmResult) {
        return new Result(
                new ProgramVmResult(
                        ProgramVmResult.Status.FAILED,
                        vmResult.nodeId(),
                        ProgramVmDiagnostic.ACTION_REJECTED
                ),
                Optional.empty()
        );
    }

    private static void finishDeferred(
            ServerPlayer player,
            Identifier category,
            ProgramInvocationContext invocation,
            ProgramActionTransaction transaction,
            ProgramSessionScheduler.Termination termination
    ) {
        if (termination.kind() != ProgramSessionScheduler.TerminationKind.COMPLETED) {
            var aborted = transaction.abort();
            var phase = switch (termination.kind()) {
                case CANCELLED -> ProgramRunStatus.Phase.CANCELLED;
                case EXPIRED -> ProgramRunStatus.Phase.EXPIRED;
                default -> ProgramRunStatus.Phase.FAILED;
            };
            ProgramRunObservations.settled(invocation, phase, termination.nodeId(), termination.diagnostic(), termination.port(),
                    termination.detail() == null ? phase.name().toLowerCase(java.util.Locale.ROOT) : termination.detail(),
                    !aborted.successful(), player.level().getGameTime());
            if (termination.kind() != ProgramSessionScheduler.TerminationKind.CANCELLED) {
                AbilityProgramManager.reportDeferredFailure(player, category, invocation,
                        termination.nodeId(), termination.kind() == ProgramSessionScheduler.TerminationKind.EXPIRED
                                ? ProgramVmDiagnostic.EXECUTION_EXPIRED
                                : aborted.successful() ? termination.diagnostic()
                                : AbilityProgramManager.actionDiagnostic(aborted.cause()),
                        termination.port(), termination.detail());
            }
            return;
        }
        var committed = transaction.commit();
        if (committed.successful()) {
            transaction.release();
            ProgramRunObservations.settled(invocation, ProgramRunStatus.Phase.COMPLETED, termination.nodeId(), ProgramVmDiagnostic.NONE,
                    null, "committed", false, player.level().getGameTime());
            return;
        }
        ProgramRunObservations.settled(invocation, ProgramRunStatus.Phase.FAILED, committed.nodeId(),
                AbilityProgramManager.actionDiagnostic(committed.cause()), null, "commit_failed", committed.rollbackFailures() > 0,
                player.level().getGameTime());
        AbilityProgramManager.reportDeferredFailure(player, category, invocation,
                committed.nodeId(), AbilityProgramManager.actionDiagnostic(committed.cause()),
                null, null);
        AcademyCraft.LOGGER.warn(
                "Deferred ability program action failed in category {} at node {}",
                category,
                committed.nodeId(),
                committed.cause()
        );
    }

    public record Result(
            ProgramVmResult vmResult,
            Optional<ProgramActionTransaction.Result> transactionResult
    ) {
        public boolean accepted() {
            return vmResult.status() == ProgramVmResult.Status.SUSPENDED
                    || vmResult.status() == ProgramVmResult.Status.FUEL_EXHAUSTED
                    || vmResult.status() == ProgramVmResult.Status.COMPLETED
                    && transactionResult.map(ProgramActionTransaction.Result::successful)
                    .orElse(false);
        }
    }
}
