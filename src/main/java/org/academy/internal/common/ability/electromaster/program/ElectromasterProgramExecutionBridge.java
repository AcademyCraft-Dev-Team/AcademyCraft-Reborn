package org.academy.internal.common.ability.electromaster.program;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.program.*;
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
 * Shared-VM execution gateway for Electromaster programs.
 */
public final class ElectromasterProgramExecutionBridge {
    private static final int MAX_FUEL = ProgramLimits.DEFAULT.maxNodes()
            * ProgramLimits.DEFAULT.maxNodes() + 1;
    private static final Map<Identifier, ProgramNodeExecutor<?>> EXECUTORS = createExecutors();

    private ElectromasterProgramExecutionBridge() {
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
                ElectromasterProgramNodeCatalog.ELECTROMASTER,
                MAX_FUEL,
                AbilityProgramDefinitions.require(
                        ElectromasterProgramNodeCatalog.ELECTROMASTER).executors(),
                new ProgramExecutionFrame(
                        transaction,
                        new ServerElectromasterProgramRuntime(player, costMultiplier),
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
            ElectromasterProgramRuntime runtime,
            ProgramActionTransaction transaction
    ) {
        return execute(program, gameTime, runtime, transaction, null, null);
    }

    private static ProgramVmResult execute(
            CompiledProgram program,
            long gameTime,
            ElectromasterProgramRuntime runtime,
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
                        ElectromasterProgramNodeCatalog.ELECTROMASTER).executors(),
                new ProgramExecutionFrame(transaction, runtime, invocation, worldGameTime)
        );
    }

    private static Map<Identifier, ProgramNodeExecutor<?>> createExecutors() {
        var result = new HashMap<Identifier, ProgramNodeExecutor<?>>();
        put(result, ElectromasterProgramNodeIds.CASTER, (context, _, _) -> data(
                "entity", ProgramValueTypes.ENTITY_REFERENCE, runtime(context).caster()));
        put(result, ElectromasterProgramNodeIds.LOOK_TARGET, (context, _, _) ->
                runtime(context).lookTarget()
                        .map(value -> data(
                                "entity", ProgramValueTypes.ENTITY_REFERENCE, value))
                        .orElseGet(() -> ProgramNodeStep.data(Map.of())));
        put(result, ElectromasterProgramNodeIds.CHARGEABLE_BLOCKS,
                (context, _, inputs) -> data(
                        "blocks",
                        ProgramValueTypes.BLOCK_POSITION_SET,
                        runtime(context).chargeableBlocksAround(
                                worldPosition(inputs, "center"),
                                floatValue(inputs, "radius"))));
        put(result, ElectromasterProgramNodeIds.MAGNETIC_ENTITIES,
                (context, _, inputs) -> data(
                        "entities",
                        ProgramValueTypes.ENTITY_SET,
                        runtime(context).magneticEntitiesAround(
                                worldPosition(inputs, "center"),
                                floatValue(inputs, "radius"))));
        put(result, ElectromasterProgramNodeIds.ENERGY_DETECTION,
                (ProgramVmContext context,
                 ElectromasterProgramNodeCatalog.EnergyDetectionConfiguration configuration,
                 ProgramInputView inputs) -> {
                    var fraction = configuration.targetType()
                            == ElectromasterProgramNodeCatalog.EnergyTargetType.ENTITY
                            ? runtime(context).entityEnergyFraction(entity(inputs, "entity"))
                            : runtime(context).blockEnergyFraction(blockPosition(inputs, "block"));
                    var threshold = configuration.percent() / 100.0;
                    var matches = fraction.isPresent() && switch (configuration.mode()) {
                        case ABOVE -> fraction.getAsDouble() > threshold;
                        case BELOW -> fraction.getAsDouble() < threshold;
                    };
                    return data("result", ProgramValueTypes.BOOLEAN, matches);
                });
        put(result, ElectromasterProgramNodeIds.ENERGY_LEVEL,
                (ProgramVmContext context,
                 ElectromasterProgramNodeCatalog.EnergyLevelConfiguration configuration,
                 ProgramInputView inputs) -> {
                    var fraction = configuration.targetType()
                            == ElectromasterProgramNodeCatalog.EnergyTargetType.ENTITY
                            ? runtime(context).entityEnergyFraction(entity(inputs, "entity"))
                            : runtime(context).blockEnergyFraction(blockPosition(inputs, "block"));
                    return ProgramNodeStep.data(Map.of(
                            "percent", new ProgramValue<>(ProgramValueTypes.FLOAT,
                                    fraction.orElse(0.0) * 100.0),
                            "available", new ProgramValue<>(ProgramValueTypes.BOOLEAN,
                                    fraction.isPresent())
                    ));
                });
        put(result, ElectromasterProgramNodeIds.REDSTONE_DETECTION,
                (ProgramVmContext context,
                 ElectromasterProgramNodeCatalog.RedstoneDetectionConfiguration configuration,
                 ProgramInputView inputs) -> {
                    var power = runtime(context).redstonePower(blockPosition(inputs, "block"));
                    var matches = switch (configuration.mode()) {
                        case ABOVE -> power > configuration.level();
                        case BELOW -> power < configuration.level();
                    };
                    return data("result", ProgramValueTypes.BOOLEAN, matches);
                });
        put(result, ElectromasterProgramNodeIds.ARC_DISCHARGE,
                (ProgramVmContext context,
                 ElectromasterProgramNodeCatalog.PowerConfiguration configuration,
                 ProgramInputView inputs) -> {
                    stage(context, runtime(context).arcDischarge(
                            entity(inputs, "entity"), configuration.power()));
                    return ProgramNodeStep.next("flow");
                });
        put(result, ElectromasterProgramNodeIds.CHAIN_DISCHARGE,
                (ProgramVmContext context,
                 ElectromasterProgramNodeCatalog.ChainConfiguration configuration,
                 ProgramInputView inputs) -> {
                    var runtime = runtime(context);
                    var targets = entities(inputs, "entities");
                    for (var index = 0;
                         index < Math.min(targets.size(), configuration.maximumJumps());
                         index++) {
                        var attenuation = Math.max(0.35f, 1.0f - index * 0.15f);
                        stage(context, runtime.arcDischarge(
                                targets.get(index), configuration.power() * attenuation));
                    }
                    return ProgramNodeStep.next("flow");
                });
        put(result, ElectromasterProgramNodeIds.MAGNETIC_MOVE,
                (ProgramVmContext context,
                 ElectromasterProgramNodeCatalog.MagneticConfiguration configuration,
                 ProgramInputView inputs) -> {
                    stage(context, runtime(context).magneticMove(
                            configuration.targetType()
                                    == ElectromasterProgramNodeCatalog.EnergyTargetType.ENTITY
                                    ? entity(inputs, "entity")
                                    : blockPosition(inputs, "block"),
                            worldPosition(inputs, "destination"),
                            configuration.power(),
                            configuration.targetType(),
                            configuration.mode(),
                            configuration.forceMagnetize()));
                    return ProgramNodeStep.next("flow");
                });
        put(result, ElectromasterProgramNodeIds.CURRENT_RECHARGE,
                (ProgramVmContext context,
                 ElectromasterProgramNodeCatalog.CurrentRechargeConfiguration configuration,
                 ProgramInputView inputs) -> {
                    stage(context, runtime(context).currentRecharge(
                            configuration.targetType()
                                    == ElectromasterProgramNodeCatalog.EnergyTargetType.ENTITY
                                    ? entity(inputs, "entity")
                                    : blockPosition(inputs, "block"),
                            configuration.targetType()));
                    return ProgramNodeStep.next("flow");
                });
        put(result, ElectromasterProgramNodeIds.STRUCTURE_EXTRACT,
                (ProgramVmContext context,
                 ElectromasterProgramNodeCatalog.ExtractStructureConfiguration configuration,
                 ProgramInputView inputs) -> structureResult(
                        runtime(context).extractStructure(
                                blockPosition(inputs, "host"),
                                configuration.budget(),
                                configuration.forceMagnetize()),
                        true,
                        false));
        put(result, ElectromasterProgramNodeIds.STRUCTURE_CONTROL,
                (context, _, inputs) -> structureResult(
                        runtime(context).controlStructure(
                                structureReference(inputs, "structure")),
                        false,
                        true));
        put(result, ElectromasterProgramNodeIds.STRUCTURE_MOVE,
                (ProgramVmContext context,
                 ElectromasterProgramNodeCatalog.MoveStructureConfiguration configuration,
                 ProgramInputView inputs) -> structureResult(
                        runtime(context).moveStructure(
                                structureControl(inputs),
                                optionalWorldPosition(inputs, "target"),
                                optionalStructureAnchor(inputs, "anchor"),
                                optionalFloat(inputs, "seconds", configuration.durationSeconds())),
                        false,
                        false));
        put(result, ElectromasterProgramNodeIds.STRUCTURE_ROTATE,
                (context, _, inputs) -> structureResult(
                        runtime(context).rotateStructure(
                                structureControl(inputs),
                                optionalStructurePose(inputs, "pose")),
                        false,
                        false));
        put(result, ElectromasterProgramNodeIds.STRUCTURE_LAUNCH,
                (ProgramVmContext context,
                 ElectromasterProgramNodeCatalog.PowerConfiguration configuration,
                 ProgramInputView inputs) -> structureResult(
                        runtime(context).launchStructure(
                                structureControl(inputs),
                                direction(inputs),
                                configuration.power()),
                        false,
                        false));
        put(result, ElectromasterProgramNodeIds.STRUCTURE_RELEASE,
                (context, _, inputs) -> structureResult(
                        runtime(context).releaseStructure(structureControl(inputs)),
                        false,
                        false));
        put(result, ElectromasterProgramNodeIds.STRUCTURE_STATICIZE,
                (context, _, inputs) -> structureResult(
                        runtime(context).staticizeStructure(
                                structureReference(inputs, "structure"),
                                optionalBlockPosition(inputs, "target")),
                        false,
                        false));
        return Map.copyOf(result);
    }

    private static ProgramNodeStep structureResult(
            ElectromasterProgramRuntime.StructureOperation operation,
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
                    ProgramVmDiagnostic.structureFailure(result),
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

    private static ElectromasterProgramRuntime runtime(ProgramVmContext context) {
        return context.attachment(ProgramExecutionFrame.class)
                .flatMap(frame -> frame.environment(ElectromasterProgramRuntime.class))
                .orElseThrow(() -> new IllegalStateException(
                        "Missing Electromaster program runtime"));
    }

    private static void stage(
            ProgramVmContext context,
            ProgramActionTransaction.ProgramAction action
    ) {
        var frame = context.attachment(ProgramExecutionFrame.class).orElseThrow();
        frame.stage(context, Objects.requireNonNull(
                action, "Electromaster runtime returned a null action"));
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

    private static ProgramBlockPosition blockPosition(ProgramInputView inputs, String port) {
        return (ProgramBlockPosition) inputs.requireCompatible(
                port, ProgramValueTypes.BLOCK_POSITION).value();
    }

    private static ProgramDirection direction(ProgramInputView inputs) {
        return (ProgramDirection) inputs.requireCompatible(
                "direction", ProgramValueTypes.DIRECTION).value();
    }

    private static ProgramStructureReference structureReference(ProgramInputView inputs, String port) {
        return (ProgramStructureReference) inputs.requireCompatible(
                port, ProgramValueTypes.STRUCTURE_REFERENCE).value();
    }

    private static ProgramStructureControl structureControl(ProgramInputView inputs) {
        return (ProgramStructureControl) inputs.requireCompatible(
                "control", ProgramValueTypes.STRUCTURE_CONTROL).value();
    }

    private static @Nullable ProgramWorldPosition optionalWorldPosition(
            ProgramInputView inputs,
            String port
    ) {
        return inputs.first(port)
                .filter(value -> ProgramValueTypes.canConnect(
                        value.type(), ProgramValueTypes.WORLD_POSITION))
                .map(value -> (ProgramWorldPosition) value.value())
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

    private static double optionalFloat(ProgramInputView inputs, String port, double fallback) {
        return inputs.first(port)
                .filter(value -> ProgramValueTypes.canConnect(
                        value.type(), ProgramValueTypes.FLOAT))
                .map(value -> ((Number) value.value()).doubleValue())
                .orElse(fallback);
    }

    private static double floatValue(ProgramInputView inputs, String port) {
        var raw = inputs.requireCompatible(port, ProgramValueTypes.FLOAT).value();
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException("Program float input is invalid");
        }
        return number.doubleValue();
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
            throw new IllegalStateException("Duplicate Electromaster program executor " + id);
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
