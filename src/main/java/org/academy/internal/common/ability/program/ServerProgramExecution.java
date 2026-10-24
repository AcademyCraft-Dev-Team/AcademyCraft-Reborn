package org.academy.internal.common.ability.program;

import org.academy.internal.common.ability.program.compile.CompiledProgram;
import org.academy.internal.common.ability.program.registry.ProgramExecutorLookup;


import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.ProgramStructureOrientationRuntime;

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
                return new Result(vmResult, Optional.of(committed));
            }
            if (vmResult.status() == ProgramVmResult.Status.FAILED) {
                var aborted = transaction.abort();
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
                transaction.abort();
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
            return;
        }
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
