package org.academy.internal.common.ability.aeromanip.program;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramLimits;
import org.academy.api.common.ability.program.ProgramValue;
import org.academy.api.common.ability.program.ProgramValueType;
import org.academy.api.common.ability.program.ProgramValueTypes;
import org.academy.internal.common.ability.program.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Shared-VM execution gateway for Aeromanip programs.
 */
public final class AeromanipProgramExecutionBridge {
    private static final int MAX_FUEL = ProgramLimits.DEFAULT.maxNodes()
            * ProgramLimits.DEFAULT.maxNodes() + 1;
    private static final Map<Identifier, ProgramNodeExecutor<?>> EXECUTORS = createExecutors();

    private AeromanipProgramExecutionBridge() {
    }

    public static ProgramExecutorLookup categoryExecutors() {
        return EXECUTORS::get;
    }

    public static ServerExecutionResult executeServer(
            CompiledProgram program,
            ServerPlayer player
    ) {
        return executeServer(program, player, 1.0f);
    }

    public static ServerExecutionResult executeServer(
            CompiledProgram program,
            ServerPlayer player,
            float costMultiplier
    ) {
        Objects.requireNonNull(player, "player");
        var transaction = new ProgramActionTransaction();
        var vmResult = execute(
                program,
                player.level().getGameTime(),
                new ServerAeromanipProgramRuntime(player, costMultiplier),
                transaction
        );
        if (vmResult.status() != ProgramVmResult.Status.COMPLETED) {
            return new ServerExecutionResult(vmResult, Optional.empty());
        }
        var commit = transaction.commit();
        if (commit.successful()) transaction.release();
        return new ServerExecutionResult(vmResult, Optional.of(commit));
    }

    public static ProgramVmResult execute(
            CompiledProgram program,
            long gameTime,
            AeromanipProgramRuntime runtime,
            ProgramActionTransaction transaction
    ) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(transaction, "transaction");
        return new ProgramVm.Session(program).run(
                gameTime,
                MAX_FUEL,
                AbilityProgramDefinitions.require(
                        AeromanipProgramNodeCatalog.AEROMANIP).executors(),
                new ProgramExecutionFrame(transaction, runtime)
        );
    }

    private static Map<Identifier, ProgramNodeExecutor<?>> createExecutors() {
        var result = new HashMap<Identifier, ProgramNodeExecutor<?>>();
        put(result, AeromanipProgramNodeIds.CASTER, (context, _, _) -> data(
                "entity", ProgramValueTypes.ENTITY_REFERENCE, runtime(context).caster()));
        put(result, AeromanipProgramNodeIds.LOOK_TARGET, (context, _, _) ->
                runtime(context).lookTarget()
                        .map(value -> data(
                                "entity", ProgramValueTypes.ENTITY_REFERENCE, value))
                        .orElseGet(() -> ProgramNodeStep.data(Map.of())));
        put(result, AeromanipProgramNodeIds.AIRFLOW_PUSH,
                (ProgramVmContext context,
                 AeromanipProgramNodeCatalog.PowerConfiguration configuration,
                 ProgramInputView inputs) -> {
                    stage(context, runtime(context).airflowPush(
                            entity(inputs, "entity"),
                            direction(inputs, "direction"),
                            configuration.power()));
                    return ProgramNodeStep.next("flow");
                });
        put(result, AeromanipProgramNodeIds.LAMINAR_CUT,
                (ProgramVmContext context,
                 AeromanipProgramNodeCatalog.PowerConfiguration configuration,
                 ProgramInputView inputs) -> {
                    stage(context, runtime(context).laminarCut(
                            direction(inputs, "direction"), configuration.power()));
                    return ProgramNodeStep.next("flow");
                });
        return Map.copyOf(result);
    }

    private static AeromanipProgramRuntime runtime(ProgramVmContext context) {
        return context.attachment(ProgramExecutionFrame.class)
                .flatMap(frame -> frame.environment(AeromanipProgramRuntime.class))
                .orElseThrow(() -> new IllegalStateException(
                        "Missing Aeromanip program runtime"));
    }

    private static void stage(
            ProgramVmContext context,
            ProgramActionTransaction.ProgramAction action
    ) {
        var frame = context.attachment(ProgramExecutionFrame.class).orElseThrow();
        frame.stage(context, Objects.requireNonNull(
                action, "Aeromanip runtime returned a null action"));
    }

    private static Object entity(ProgramInputView inputs, String port) {
        return inputs.requireCompatible(port, ProgramValueTypes.ENTITY_REFERENCE).value();
    }

    private static ProgramDirection direction(ProgramInputView inputs, String port) {
        return (ProgramDirection) inputs.requireCompatible(
                port, ProgramValueTypes.DIRECTION).value();
    }

    private static <T> ProgramNodeStep data(
            String port,
            ProgramValueType type,
            T value
    ) {
        return ProgramNodeStep.data(Map.of(
                port,
                new ProgramValue<>(type, Objects.requireNonNull(value, "Program output"))
        ));
    }

    private static <C> void put(
            Map<Identifier, ProgramNodeExecutor<?>> result,
            Identifier id,
            ProgramNodeExecutor<C> executor
    ) {
        if (result.putIfAbsent(id, executor) != null) {
            throw new IllegalStateException("Duplicate Aeromanip program executor " + id);
        }
    }

    public record ServerExecutionResult(
            ProgramVmResult vmResult,
            Optional<ProgramActionTransaction.Result> transactionResult
    ) {
        public boolean successful() {
            return vmResult.status() == ProgramVmResult.Status.COMPLETED
                    && transactionResult.map(ProgramActionTransaction.Result::successful)
                    .orElse(false);
        }
    }
}
