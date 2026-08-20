package org.academy.internal.common.ability.electromaster.program;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.program.ProgramLimits;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramValue;
import org.academy.api.common.ability.program.ProgramValueTypes;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.internal.common.ability.program.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.CompiledProgram;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.academy.internal.common.ability.program.ProgramExecutionFrame;
import org.academy.internal.common.ability.program.ProgramExecutorLookup;
import org.academy.internal.common.ability.program.ProgramInputView;
import org.academy.internal.common.ability.program.ProgramNodeExecutor;
import org.academy.internal.common.ability.program.ProgramNodeStep;
import org.academy.internal.common.ability.program.ProgramVm;
import org.academy.internal.common.ability.program.ProgramVmContext;
import org.academy.internal.common.ability.program.ProgramVmResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Shared-VM execution gateway for Electromaster programs. */
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
        Objects.requireNonNull(player, "player");
        var transaction = new ProgramActionTransaction();
        var vmResult = execute(
                program,
                player.level().getGameTime(),
                new ServerElectromasterProgramRuntime(player, costMultiplier),
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
            ElectromasterProgramRuntime runtime,
            ProgramActionTransaction transaction
    ) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(transaction, "transaction");
        return new ProgramVm.Session(program).run(
                gameTime,
                MAX_FUEL,
                AbilityProgramDefinitions.require(
                        ElectromasterProgramNodeCatalog.ELECTROMASTER).executors(),
                new ProgramExecutionFrame(transaction, runtime)
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
                            configuration.mode()));
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
        return Map.copyOf(result);
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

    private static ProgramWorldPosition worldPosition(ProgramInputView inputs, String port) {
        return (ProgramWorldPosition) inputs.requireCompatible(
                port, ProgramValueTypes.WORLD_POSITION).value();
    }

    private static ProgramBlockPosition blockPosition(ProgramInputView inputs, String port) {
        return (ProgramBlockPosition) inputs.requireCompatible(
                port, ProgramValueTypes.BLOCK_POSITION).value();
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
            org.academy.api.common.ability.program.ProgramValueType type,
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
            return vmResult.status() == ProgramVmResult.Status.COMPLETED
                    && transactionResult.map(ProgramActionTransaction.Result::successful)
                    .orElse(false);
        }
    }
}
