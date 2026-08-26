package org.academy.internal.common.ability.teleport.program;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.program.*;
import org.academy.internal.common.ability.program.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Shared-VM execution gateway for Teleport programs.
 */
public final class TeleportProgramExecutionBridge {
    private static final int MAX_FUEL = ProgramLimits.DEFAULT.maxNodes()
            * ProgramLimits.DEFAULT.maxNodes() + 1;
    private static final Map<Identifier, ProgramNodeExecutor<?>> EXECUTORS = createExecutors();

    private TeleportProgramExecutionBridge() {
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
                new ServerTeleportProgramRuntime(player, costMultiplier),
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
            TeleportProgramRuntime runtime,
            ProgramActionTransaction transaction
    ) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(transaction, "transaction");
        return new ProgramVm.Session(program).run(
                gameTime,
                MAX_FUEL,
                AbilityProgramDefinitions.require(
                        TeleportProgramNodeCatalog.TELEPORT).executors(),
                new ProgramExecutionFrame(transaction, runtime)
        );
    }

    private static Map<Identifier, ProgramNodeExecutor<?>> createExecutors() {
        var result = new HashMap<Identifier, ProgramNodeExecutor<?>>();
        put(result, TeleportProgramNodeIds.CASTER, (context, _, _) -> data(
                "entity", ProgramValueTypes.ENTITY_REFERENCE, runtime(context).caster()));
        put(result, TeleportProgramNodeIds.LOOK_TARGET, (context, _, _) ->
                runtime(context).lookTarget()
                        .map(value -> data(
                                "entity", ProgramValueTypes.ENTITY_REFERENCE, value))
                        .orElseGet(() -> ProgramNodeStep.data(Map.of())));
        put(result, TeleportProgramNodeIds.SELF_TELEPORT,
                (ProgramVmContext context,
                 TeleportProgramNodeCatalog.PowerConfiguration configuration,
                 ProgramInputView inputs) -> {
                    stage(context, runtime(context).teleportSelf(
                            worldPosition(inputs, "destination"), configuration.power()));
                    return ProgramNodeStep.next("flow");
                });
        put(result, TeleportProgramNodeIds.ENTITY_TELEPORT,
                (ProgramVmContext context,
                 TeleportProgramNodeCatalog.TargetTeleportConfiguration configuration,
                 ProgramInputView inputs) -> {
                    stage(context, runtime(context).teleportEntity(
                            configuration.targetType() == TeleportProgramNodeCatalog.TargetType.ENTITY
                                    ? entity(inputs, "entity")
                                    : blockPosition(inputs, "block"),
                            inputs.requireCompatible(
                                    "destination", ProgramValueTypes.CONTROL_DESTINATION).value(),
                            optionalDirection(inputs, "direction"),
                            configuration.power(),
                            configuration.targetType()));
                    return ProgramNodeStep.next("flow");
                });
        put(result, TeleportProgramNodeIds.BLOCK_ITEM_TELEPORT,
                (ProgramVmContext context,
                 TeleportProgramNodeCatalog.BlockItemTeleportConfiguration configuration,
                 ProgramInputView inputs) -> {
                    var slot = inputs.first("slot")
                            .map(value -> (Integer) value.value())
                            .orElse(0);
                    stage(context, runtime(context).teleportBlockOrItem(
                            blockPosition(inputs, "position"), slot, configuration.mode()));
                    return ProgramNodeStep.next("flow");
                });
        put(result, TeleportProgramNodeIds.SPACE_SAFETY, (context, _, inputs) -> data(
                "result",
                ProgramValueTypes.BOOLEAN,
                runtime(context).isSpaceSafe(
                        entity(inputs, "entity"), worldPosition(inputs, "position"))));
        return Map.copyOf(result);
    }

    private static TeleportProgramRuntime runtime(ProgramVmContext context) {
        return context.attachment(ProgramExecutionFrame.class)
                .flatMap(frame -> frame.environment(TeleportProgramRuntime.class))
                .orElseThrow(() -> new IllegalStateException(
                        "Missing Teleport program runtime"));
    }

    private static void stage(
            ProgramVmContext context,
            ProgramActionTransaction.ProgramAction action
    ) {
        var frame = context.attachment(ProgramExecutionFrame.class).orElseThrow();
        frame.stage(context, Objects.requireNonNull(
                action, "Teleport runtime returned a null action"));
    }

    private static Object entity(ProgramInputView inputs, String port) {
        return inputs.requireCompatible(port, ProgramValueTypes.ENTITY_REFERENCE).value();
    }

    private static ProgramWorldPosition worldPosition(ProgramInputView inputs, String port) {
        return (ProgramWorldPosition) inputs.requireCompatible(
                port, ProgramValueTypes.WORLD_POSITION).value();
    }

    private static ProgramBlockPosition blockPosition(
            ProgramInputView inputs,
            String port
    ) {
        return (ProgramBlockPosition)
                inputs.requireCompatible(port, ProgramValueTypes.BLOCK_POSITION).value();
    }

    private static ProgramDirection optionalDirection(ProgramInputView inputs, String port) {
        return inputs.first(port).map(value -> (ProgramDirection) value.value()).orElse(null);
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
            throw new IllegalStateException("Duplicate Teleport program executor " + id);
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
