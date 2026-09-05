package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.AcademyCraft;

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
        var session = new ProgramVm.Session(program);
        var now = player.level().getGameTime();
        var vmResult = session.run(now, fuelPerTick, executors, frame);
        if (vmResult.status() == ProgramVmResult.Status.COMPLETED) {
            var committed = transaction.commit();
            if (committed.successful()) transaction.release();
            return new Result(vmResult, Optional.of(committed));
        }
        if (vmResult.status() == ProgramVmResult.Status.FAILED) {
            return new Result(vmResult, Optional.empty());
        }
        if (invocation == null) {
            return rejected(vmResult);
        }

        var key = new ServerProgramScheduler.SessionKey(
                player.getUUID(), category, invocation.programId(), invocation.slot());
        var scheduled = ServerProgramScheduler.resume(
                player.level().getServer(),
                key,
                session,
                executors,
                frame,
                fuelPerTick,
                now + 1L,
                MAX_LIFETIME_TICKS,
                (_, termination) -> finishDeferred(player, category, invocation, transaction, termination)
        );
        if (!scheduled) {
            return rejected(vmResult);
        }
        return new Result(vmResult, Optional.empty());
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
            if (termination.kind() != ProgramSessionScheduler.TerminationKind.CANCELLED) {
                AbilityProgramManager.reportDeferredFailure(player, category, invocation,
                        termination.nodeId(), termination.kind() == ProgramSessionScheduler.TerminationKind.EXPIRED
                                ? ProgramVmDiagnostic.EXECUTION_EXPIRED : termination.diagnostic());
            }
            return;
        }
        var committed = transaction.commit();
        if (committed.successful()) {
            transaction.release();
            return;
        }
        AbilityProgramManager.reportDeferredFailure(player, category, invocation,
                committed.nodeId(), AbilityProgramManager.actionDiagnostic(committed.cause()));
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
