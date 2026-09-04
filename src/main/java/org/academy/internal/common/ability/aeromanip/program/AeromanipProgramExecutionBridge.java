package org.academy.internal.common.ability.aeromanip.program;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.program.*;
import org.academy.internal.common.ability.aeromanip.AeromanipChargeTier;
import org.academy.internal.common.ability.program.*;

import java.util.HashMap;
import java.util.List;
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
        return executeServer(program, player, costMultiplier, null);
    }

    public static ServerExecutionResult executeServer(
            CompiledProgram program,
            ServerPlayer player,
            float costMultiplier,
            ProgramInvocationContext invocation
    ) {
        Objects.requireNonNull(player, "player");
        var transaction = new ProgramActionTransaction();
        var execution = ServerProgramExecution.execute(
                program,
                player,
                AeromanipProgramNodeCatalog.AEROMANIP,
                MAX_FUEL,
                AbilityProgramDefinitions.require(
                        AeromanipProgramNodeCatalog.AEROMANIP).executors(),
                new ProgramExecutionFrame(
                        transaction,
                        new ServerAeromanipProgramRuntime(player, costMultiplier),
                        invocation,
                        player.level()::getGameTime
                ),
                transaction,
                invocation
        );
        return new ServerExecutionResult(
                execution.vmResult(), execution.transactionResult());
    }

    public static ProgramVmResult execute(
            CompiledProgram program,
            long gameTime,
            AeromanipProgramRuntime runtime,
            ProgramActionTransaction transaction
    ) {
        return execute(program, gameTime, runtime, transaction, null, null);
    }

    private static ProgramVmResult execute(
            CompiledProgram program,
            long gameTime,
            AeromanipProgramRuntime runtime,
            ProgramActionTransaction transaction,
            ProgramInvocationContext invocation,
            java.util.function.LongSupplier worldGameTime
    ) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(transaction, "transaction");
        return new ProgramVm.Session(program).run(
                gameTime,
                MAX_FUEL,
                AbilityProgramDefinitions.require(
                        AeromanipProgramNodeCatalog.AEROMANIP).executors(),
                new ProgramExecutionFrame(transaction, runtime, invocation, worldGameTime)
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
        put(result, AeromanipProgramNodeIds.CONVERGING_AIRFLOW,
                (ProgramVmContext context,
                 AeromanipProgramNodeCatalog.ConvergingAirflowConfiguration configuration,
                 ProgramInputView inputs) -> {
                    var runtime = runtime(context);
                    var center = worldPosition(inputs, "center");
                    var targets = entities(inputs, "entities");
                    for (var index = 0;
                         index < Math.min(targets.size(), configuration.maximumTargets());
                         index++) {
                        var entity = targets.get(index);
                        var position = runtime.positionOf(entity).orElse(null);
                        if (position == null
                                || !position.dimension().equals(center.dimension())
                                || squaredDistance(position, center) < 1.0e-12) continue;
                        stage(context, runtime.airflowPush(
                                entity,
                                ProgramDirection.between(position, center),
                                configuration.power()));
                    }
                    return ProgramNodeStep.next("flow");
                });
        put(result, AeromanipProgramNodeIds.LAMINAR_CUT,
                (ProgramVmContext context,
                 AeromanipProgramNodeCatalog.LaminarCutConfiguration configuration,
                 ProgramInputView inputs) -> {
                    stage(context, runtime(context).laminarCut(
                            optionalWorldPosition(inputs, "origin"),
                            direction(inputs, "direction"),
                            configuration.power(),
                            chargeTier(configuration.chargeTier()),
                            configuration.chargeAcceleration().costMultiplier(),
                            optionalDirection(inputs, "plane_direction"),
                            configuration.planeMode()));
                    var delay = chargeDelayTicks(
                            configuration.chargeTier(),
                            configuration.chargeAcceleration());
                    return delay == 0
                            ? ProgramNodeStep.next("flow")
                            : ProgramNodeStep.yield("flow", delay);
                });
        put(result, AeromanipProgramNodeIds.PLACE_TEMPORARY_JET_NOZZLE,
                (ProgramVmContext context,
                 AeromanipProgramNodeCatalog.TemporaryNozzleConfiguration configuration,
                 ProgramInputView inputs) -> {
                    var target = configuration.targetType()
                            == AeromanipProgramNodeCatalog.NozzleTargetType.ENTITY
                            ? entity(inputs, "entity")
                            : blockPosition(inputs, "block");
                    stage(context, runtime(context).placeTemporaryJetNozzle(
                            target,
                            direction(inputs, "direction"),
                            configuration.targetType()));
                    return ProgramNodeStep.next("flow");
                });
        put(result, AeromanipProgramNodeIds.FIRE_JETS,
                (ProgramVmContext context,
                 AeromanipProgramNodeCatalog.JetActivationConfiguration configuration,
                 ProgramInputView inputs) -> {
                    stage(context, runtime(context).fireJets(configuration.duration()));
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

    private static List<?> entities(ProgramInputView inputs, String port) {
        var value = inputs.requireCompatible(port, ProgramValueTypes.ENTITY_SET).value();
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Program entity set input is invalid");
        }
        return list;
    }

    private static ProgramWorldPosition worldPosition(ProgramInputView inputs, String port) {
        return (ProgramWorldPosition) inputs.requireCompatible(
                port, ProgramValueTypes.WORLD_POSITION).value();
    }

    private static double squaredDistance(
            ProgramWorldPosition left,
            ProgramWorldPosition right
    ) {
        var x = left.x() - right.x();
        var y = left.y() - right.y();
        var z = left.z() - right.z();
        return x * x + y * y + z * z;
    }

    private static ProgramDirection direction(ProgramInputView inputs, String port) {
        return (ProgramDirection) inputs.requireCompatible(
                port, ProgramValueTypes.DIRECTION).value();
    }

    private static ProgramDirection optionalDirection(ProgramInputView inputs, String port) {
        return inputs.first(port)
                .map(value -> (ProgramDirection) value.value())
                .orElse(null);
    }

    private static ProgramWorldPosition optionalWorldPosition(
            ProgramInputView inputs,
            String port
    ) {
        return inputs.first(port)
                .map(value -> (ProgramWorldPosition) value.value())
                .orElse(null);
    }

    static long chargeDelayTicks(
            AeromanipProgramNodeCatalog.ChargeTier tier,
            AeromanipProgramNodeCatalog.ChargeAcceleration acceleration
    ) {
        var baseTicks = switch (tier) {
            case INSTANT -> 0;
            case HALF -> AeromanipChargeTier.HALF_CHARGE_TICKS;
            case FULL -> AeromanipChargeTier.FULL_CHARGE_TICKS;
        };
        return switch (acceleration) {
            case STANDARD -> baseTicks;
            case ACCELERATED -> (baseTicks + 1L) / 2L;
            case INSTANT -> 0L;
        };
    }

    private static ProgramBlockPosition blockPosition(ProgramInputView inputs, String port) {
        return (ProgramBlockPosition) inputs.requireCompatible(
                port, ProgramValueTypes.BLOCK_POSITION).value();
    }

    private static AeromanipChargeTier chargeTier(
            AeromanipProgramNodeCatalog.ChargeTier tier
    ) {
        return switch (tier) {
            case INSTANT -> AeromanipChargeTier.INSTANT;
            case HALF -> AeromanipChargeTier.HALF;
            case FULL -> AeromanipChargeTier.FULL;
        };
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
            return vmResult.status() == ProgramVmResult.Status.SUSPENDED
                    || vmResult.status() == ProgramVmResult.Status.FUEL_EXHAUSTED
                    || vmResult.status() == ProgramVmResult.Status.COMPLETED
                    && transactionResult.map(ProgramActionTransaction.Result::successful)
                    .orElse(false);
        }
    }
}
