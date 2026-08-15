package org.academy.internal.common.ability.teleport.program;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.program.ProgramLimits;
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

/** Shared-VM execution gateway for Teleport programs. */
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
        Objects.requireNonNull(player, "player");
        var transaction = new ProgramActionTransaction();
        var vmResult = execute(
                program,
                player.level().getGameTime(),
                new ServerTeleportProgramRuntime(player),
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
                 TeleportProgramNodeCatalog.PowerConfiguration configuration,
                 ProgramInputView inputs) -> {
                    stage(context, runtime(context).teleportEntity(
                            entity(inputs, "entity"),
                            worldPosition(inputs, "destination"),
                            configuration.power()));
                    return ProgramNodeStep.next("flow");
                });
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
