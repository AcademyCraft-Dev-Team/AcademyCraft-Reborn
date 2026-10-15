package org.academy.internal.common.ability.aeromanip.program;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.program.*;
import org.academy.internal.common.ability.aeromanip.charge.AeromanipChargeTier;
import org.academy.internal.common.ability.program.*;
import org.academy.internal.common.ability.program.compile.CompiledProgram;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.ProgramExecutorLookup;
import org.academy.internal.common.ability.program.registry.ProgramNodeExecutor;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

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
        var transaction = ProgramActionTransaction.sequential();
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
            LongSupplier worldGameTime
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
        put(result, AeromanipProgramNodeIds.OWNED_JET_NOZZLE_COUNT,
                (context, _, inputs) -> data(
                        "count",
                        ProgramValueTypes.INTEGER,
                        runtime(context).ownedJetNozzleCount(
                                worldPosition(inputs, "center"),
                                floatValue(inputs, "radius"),
                                optionalDirection(inputs, "direction")
                        )
                ));
        put(result, AeromanipProgramNodeIds.LAUNCH_BLOCK_STRUCTURE,
                (ProgramVmContext context,
                 AeromanipProgramNodeCatalog.BlockStructureLaunchConfiguration configuration,
                 ProgramInputView inputs) -> {
                    stage(context, runtime(context).launchBlockStructure(
                            blockPosition(inputs, "block"),
                            direction(inputs, "direction"),
                            configuration
                    ));
                    return ProgramNodeStep.next("flow");
                });
        put(result, AeromanipProgramNodeIds.STRUCTURE_EXTRACT,
                (ProgramVmContext context,
                 AeromanipProgramNodeCatalog.ExtractStructureConfiguration configuration,
                 ProgramInputView inputs) -> structureResult(
                        runtime(context).extractStructure(
                                blockPosition(inputs, "host"),
                                configuration.radius(),
                                configuration.budget()),
                        true,
                        false));
        put(result, AeromanipProgramNodeIds.STRUCTURE_CONTROL,
                (context, _, inputs) -> structureResult(
                        runtime(context).controlStructure(
                                structureReference(inputs, "structure")),
                        false,
                        true));
        put(result, AeromanipProgramNodeIds.STRUCTURE_MOVE,
                (ProgramVmContext context,
                 AeromanipProgramNodeCatalog.MoveStructureConfiguration configuration,
                 ProgramInputView inputs) -> structureResult(
                        runtime(context).moveStructure(
                                structureControl(inputs),
                                optionalWorldPosition(inputs, "target"),
                                optionalStructureAnchor(inputs, "anchor"),
                                optionalFloat(inputs, "seconds", configuration.durationSeconds())),
                        false,
                        false));
        put(result, AeromanipProgramNodeIds.STRUCTURE_ROTATE,
                (context, _, inputs) -> structureResult(
                        runtime(context).rotateStructure(
                                structureControl(inputs),
                                optionalStructurePose(inputs, "pose")),
                        false,
                        false));
        put(result, AeromanipProgramNodeIds.STRUCTURE_LAUNCH,
                (ProgramVmContext context,
                 AeromanipProgramNodeCatalog.LaunchStructureConfiguration configuration,
                 ProgramInputView inputs) -> structureResult(
                        runtime(context).launchStructure(
                                structureControl(inputs),
                                direction(inputs, "direction"),
                                configuration.power(),
                                configuration.durationSeconds()),
                        false,
                        false));
        put(result, AeromanipProgramNodeIds.STRUCTURE_RELEASE,
                (context, _, inputs) -> structureResult(
                        runtime(context).releaseStructure(structureControl(inputs)),
                        false,
                        false));
        put(result, AeromanipProgramNodeIds.STRUCTURE_STATICIZE,
                (context, _, inputs) -> structureResult(
                        runtime(context).staticizeStructure(
                                structureReference(inputs, "structure"),
                                optionalBlockPosition(inputs, "target")),
                        false,
                        false));
        return Map.copyOf(result);
    }

    private static ProgramNodeStep structureResult(
            AeromanipProgramRuntime.StructureOperation operation,
            boolean includeStructure,
            boolean includeControl
    ) {
        var result = operation.result();
        if (result.status() == ProgramStructureOperationResult.Status.PENDING) {
            return ProgramNodeStep.retry(1);
        }
        if (result.status() != ProgramStructureOperationResult.Status.COMPLETED
                && result.status() != ProgramStructureOperationResult.Status.PENDING) {
            throw new ProgramActionFailure(
                    result.status() == ProgramStructureOperationResult.Status.UNAVAILABLE
                            ? ProgramVmDiagnostic.ACTION_UNAVAILABLE
                            : ProgramVmDiagnostic.ACTION_REJECTED,
                    statusDetail(result));
        }
        if (result.status() == ProgramStructureOperationResult.Status.COMPLETED) {
            if (includeStructure && operation.structure() == null) {
                throw new ProgramActionFailure(ProgramVmDiagnostic.ACTION_REJECTED,
                        statusDetail(result, "structure_missing"));
            }
            if (includeControl && operation.control() == null) {
                throw new ProgramActionFailure(ProgramVmDiagnostic.ACTION_REJECTED,
                        statusDetail(result, "control_missing"));
            }
        }
        var outputs = new HashMap<String, ProgramValue<?>>();
        outputs.put("result", new ProgramValue<>(
                ProgramValueTypes.STRUCTURE_OPERATION_RESULT,
                result
        ));
        if (includeStructure && operation.structure() != null) {
            outputs.put("structure", new ProgramValue<>(
                    ProgramValueTypes.STRUCTURE_REFERENCE,
                    operation.structure()
            ));
        }
        if (includeControl && operation.control() != null) {
            outputs.put("control", new ProgramValue<>(
                    ProgramValueTypes.STRUCTURE_CONTROL,
                    operation.control()
            ));
        }
        return ProgramNodeStep.next("flow", outputs);
    }

    private static String statusDetail(ProgramStructureOperationResult result) {
        return statusDetail(result, result.detail());
    }

    private static String statusDetail(ProgramStructureOperationResult result, String detail) {
        return result.status().name().toLowerCase(Locale.ROOT) + ":" + detail;
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

    private static double floatValue(ProgramInputView inputs, String port) {
        var value = inputs.requireCompatible(port, ProgramValueTypes.FLOAT).value();
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException("Program float input is invalid");
        }
        return number.doubleValue();
    }

    private static ProgramWorldPosition optionalWorldPosition(
            ProgramInputView inputs,
            String port
    ) {
        return inputs.first(port)
                .map(value -> (ProgramWorldPosition) value.value())
                .orElse(null);
    }

    private static ProgramStructureReference structureReference(
            ProgramInputView inputs,
            String port
    ) {
        return (ProgramStructureReference) inputs.requireCompatible(
                port, ProgramValueTypes.STRUCTURE_REFERENCE).value();
    }

    private static ProgramStructureControl structureControl(ProgramInputView inputs) {
        return (ProgramStructureControl) inputs.requireCompatible(
                "control", ProgramValueTypes.STRUCTURE_CONTROL).value();
    }

    private static @Nullable ProgramStructureAnchor optionalStructureAnchor(
            ProgramInputView inputs,
            String port
    ) {
        return inputs.first(port)
                .filter(value -> value.type().equals(ProgramValueTypes.STRUCTURE_ANCHOR))
                .map(value -> (ProgramStructureAnchor) value.value())
                .orElse(null);
    }

    private static @Nullable ProgramStructurePose optionalStructurePose(
            ProgramInputView inputs,
            String port
    ) {
        return inputs.first(port)
                .filter(value -> value.type().equals(ProgramValueTypes.STRUCTURE_POSE))
                .map(value -> (ProgramStructurePose) value.value())
                .orElse(null);
    }

    private static @Nullable ProgramBlockPosition optionalBlockPosition(
            ProgramInputView inputs,
            String port
    ) {
        return inputs.first(port)
                .filter(value -> ProgramValueTypes.canConnect(
                        value.type(), ProgramValueTypes.BLOCK_POSITION))
                .map(value -> (ProgramBlockPosition) value.value())
                .orElse(null);
    }

    private static double optionalFloat(ProgramInputView inputs, String port, double fallback) {
        return inputs.first(port)
                .filter(value -> ProgramValueTypes.canConnect(
                        value.type(), ProgramValueTypes.FLOAT))
                .map(value -> ((Number) value.value()).doubleValue())
                .orElse(fallback);
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
