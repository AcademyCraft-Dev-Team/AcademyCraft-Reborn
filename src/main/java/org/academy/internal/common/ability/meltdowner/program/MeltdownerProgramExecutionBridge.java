package org.academy.internal.common.ability.meltdowner.program;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.program.*;
import org.academy.internal.common.ability.program.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Shared-VM execution gateway for Meltdowner programs.
 */
public final class MeltdownerProgramExecutionBridge {
    private static final int MAX_FUEL = ProgramLimits.DEFAULT.maxNodes()
            * ProgramLimits.DEFAULT.maxNodes() + 1;
    private static final Map<Identifier, ProgramNodeExecutor<?>> EXECUTORS = createExecutors();

    private MeltdownerProgramExecutionBridge() {
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
                MeltdownerProgramNodeCatalog.MELTDOWNER,
                MAX_FUEL,
                AbilityProgramDefinitions.require(
                        MeltdownerProgramNodeCatalog.MELTDOWNER).executors(),
                new ProgramExecutionFrame(
                        transaction,
                        new ServerMeltdownerProgramRuntime(player, costMultiplier),
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
            MeltdownerProgramRuntime runtime,
            ProgramActionTransaction transaction
    ) {
        return execute(program, gameTime, runtime, transaction, null, null);
    }

    private static ProgramVmResult execute(
            CompiledProgram program,
            long gameTime,
            MeltdownerProgramRuntime runtime,
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
                        MeltdownerProgramNodeCatalog.MELTDOWNER).executors(),
                new ProgramExecutionFrame(transaction, runtime, invocation, worldGameTime)
        );
    }

    private static Map<Identifier, ProgramNodeExecutor<?>> createExecutors() {
        var result = new HashMap<Identifier, ProgramNodeExecutor<?>>();
        put(result, MeltdownerProgramNodeIds.CASTER, (context, _, _) -> data(
                "entity", ProgramValueTypes.ENTITY_REFERENCE, runtime(context).caster()));
        put(result, MeltdownerProgramNodeIds.LOOK_TARGET, (context, _, _) ->
                runtime(context).lookTarget()
                        .map(value -> data(
                                "entity", ProgramValueTypes.ENTITY_REFERENCE, value))
                        .orElseGet(() -> ProgramNodeStep.data(Map.of())));
        put(result, MeltdownerProgramNodeIds.ELECTRON_BEAM,
                (ProgramVmContext context,
                 MeltdownerProgramNodeCatalog.BeamConfiguration configuration,
                 ProgramInputView inputs) -> {
                    stage(context, runtime(context).fireElectronBeam(
                            optionalWorldPosition(inputs, "origin"),
                            configuration.aimMode() == MeltdownerProgramNodeCatalog.AimMode.DIRECTION
                                    ? optionalDirection(inputs, "direction") : null,
                            configuration.aimMode() == MeltdownerProgramNodeCatalog.AimMode.TARGET
                                    ? optionalWorldPosition(inputs, "target_position") : null,
                            configuration.power(),
                            configuration.destroyBlocks(),
                            configuration.destroyProjectiles()));
                    return ProgramNodeStep.next("flow");
                });
        put(result, MeltdownerProgramNodeIds.ELECTRON_FAN,
                (ProgramVmContext context,
                 MeltdownerProgramNodeCatalog.ElectronFanConfiguration configuration,
                 ProgramInputView inputs) -> {
                    var runtime = runtime(context);
                    var origin = optionalWorldPosition(inputs, "origin");
                    for (var direction : fanDirections(
                            direction(inputs, "direction"),
                            configuration.beamCount(),
                            configuration.spreadDegrees())) {
                        stage(context, runtime.fireElectronBeam(
                                origin,
                                direction,
                                null,
                                configuration.power(),
                                configuration.destroyBlocks(),
                                false));
                    }
                    return ProgramNodeStep.next("flow");
                });
        put(result, MeltdownerProgramNodeIds.MINING_BEAM,
                (ProgramVmContext context,
                 MeltdownerProgramNodeCatalog.MiningBeamConfiguration configuration,
                 ProgramInputView inputs) -> {
                    stage(context, runtime(context).fireMiningBeam(
                            optionalWorldPosition(inputs, "origin"),
                            configuration.aimMode() == MeltdownerProgramNodeCatalog.AimMode.DIRECTION
                                    ? optionalDirection(inputs, "direction") : null,
                            configuration.aimMode() == MeltdownerProgramNodeCatalog.AimMode.TARGET
                                    ? optionalWorldPosition(inputs, "target_position") : null,
                            optionalBlockPosition(inputs, "block"),
                            configuration.power()));
                    return ProgramNodeStep.next("flow");
                });
        put(result, MeltdownerProgramNodeIds.ATOMIC_JET,
                (ProgramVmContext context,
                 MeltdownerProgramNodeCatalog.AtomicJetConfiguration configuration,
                 ProgramInputView inputs) -> {
                    stage(context, runtime(context).atomicJet(
                            entity(inputs, "entity"),
                            direction(inputs, "direction"),
                            configuration.power(),
                            configuration.destroyBlocks()));
                    return ProgramNodeStep.next("flow");
                });
        return Map.copyOf(result);
    }

    private static MeltdownerProgramRuntime runtime(ProgramVmContext context) {
        return context.attachment(ProgramExecutionFrame.class)
                .flatMap(frame -> frame.environment(MeltdownerProgramRuntime.class))
                .orElseThrow(() -> new IllegalStateException(
                        "Missing Meltdowner program runtime"));
    }

    private static void stage(
            ProgramVmContext context,
            ProgramActionTransaction.ProgramAction action
    ) {
        var frame = context.attachment(ProgramExecutionFrame.class).orElseThrow();
        frame.stage(context, Objects.requireNonNull(
                action, "Meltdowner runtime returned a null action"));
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

    private static ProgramBlockPosition optionalBlockPosition(
            ProgramInputView inputs,
            String port
    ) {
        return inputs.first(port)
                .map(value -> (ProgramBlockPosition) value.value())
                .orElse(null);
    }

    private static Object entity(ProgramInputView inputs, String port) {
        return inputs.requireCompatible(port, ProgramValueTypes.ENTITY_REFERENCE).value();
    }

    static java.util.List<ProgramDirection> fanDirections(
            ProgramDirection forward,
            int count,
            float spreadDegrees
    ) {
        if (count < 2 || count > 8) {
            throw new IllegalArgumentException("Electron fan beam count is outside limits");
        }
        if (!Float.isFinite(spreadDegrees) || spreadDegrees < 0.0f || spreadDegrees > 30.0f) {
            throw new IllegalArgumentException("Electron fan spread is outside limits");
        }
        var f = new ProgramVector(forward.x(), forward.y(), forward.z());
        var reference = Math.abs(forward.y()) < 0.95
                ? new ProgramVector(0.0, 1.0, 0.0)
                : new ProgramVector(1.0, 0.0, 0.0);
        var right = f.cross(reference).direction();
        var up = ProgramVector.of(right).cross(f).direction();
        var tangent = Math.tan(Math.toRadians(spreadDegrees));
        var result = new java.util.ArrayList<ProgramDirection>(count);
        result.add(forward);
        for (var index = 1; index < count; index++) {
            var angle = (index - 1) * Math.PI * 2.0 / (count - 1);
            var offset = ProgramVector.of(right).scale(Math.cos(angle) * tangent)
                    .add(ProgramVector.of(up).scale(Math.sin(angle) * tangent));
            result.add(f.add(offset).direction());
        }
        return java.util.List.copyOf(result);
    }

    private static ProgramBlockPosition blockPosition(ProgramInputView inputs, String port) {
        return (ProgramBlockPosition) inputs.requireCompatible(
                port, ProgramValueTypes.BLOCK_POSITION).value();
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
            throw new IllegalStateException("Duplicate Meltdowner program executor " + id);
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
