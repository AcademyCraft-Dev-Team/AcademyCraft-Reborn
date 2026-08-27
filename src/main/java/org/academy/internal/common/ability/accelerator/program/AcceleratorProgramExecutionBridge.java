package org.academy.internal.common.ability.accelerator.program;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.program.*;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.program.*;
import org.academy.internal.server.world.level.storage.Player;

import java.util.*;

/**
 * Shared-VM execution gateway for vector-manipulation programs.
 */
public final class AcceleratorProgramExecutionBridge {
    private static final int MAX_FUEL = ProgramLimits.DEFAULT.maxNodes()
            * ProgramLimits.DEFAULT.maxNodes() + 1;
    private static final Map<Identifier, ProgramNodeExecutor<?>> EXECUTORS = createExecutors();

    private AcceleratorProgramExecutionBridge() {
    }

    public static ProgramExecutorLookup categoryExecutors() {
        return EXECUTORS::get;
    }

    /**
     * Compiles against the player's learned accelerator skills.
     */
    public static ProgramCompileResult compileFor(ServerPlayer player, ProgramGraph graph) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(graph, "graph");
        var data = AbilitySystemServer.getSystem(player).getPlayerData(player.getUUID());
        var capabilities = new HashSet<Identifier>();
        addLearnedCapability(data, capabilities, Skills.VECTOR_ACCEL.get().getKeyString());
        addLearnedCapability(data, capabilities,
                Skills.KINETIC_ENERGY_APPLIED.get().getKeyString());
        addLearnedCapability(data, capabilities, Skills.VECTOR_REFLECTION.get().getKeyString());
        return AbilityProgramDefinitions.require(AcceleratorProgramNodeCatalog.ACCELERATOR)
                .compile(graph, Set.copyOf(capabilities));
    }

    /**
     * Executes, validates and atomically commits a compiled program on the server.
     */
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
                new ServerAcceleratorProgramRuntime(player, costMultiplier),
                transaction
        );
        if (vmResult.status() != ProgramVmResult.Status.COMPLETED) {
            return new ServerExecutionResult(vmResult, Optional.empty());
        }
        var commit = transaction.commit();
        if (commit.successful()) transaction.release();
        return new ServerExecutionResult(vmResult, Optional.of(commit));
    }

    /**
     * Executes and stages actions into {@code transaction}. The caller remains responsible for
     * committing and releasing the transaction after a completed result.
     */
    public static ProgramVmResult execute(
            CompiledProgram program,
            long gameTime,
            AcceleratorProgramRuntime runtime,
            ProgramActionTransaction transaction
    ) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(transaction, "transaction");
        return new ProgramVm.Session(program).run(
                gameTime,
                MAX_FUEL,
                AbilityProgramDefinitions.require(
                        AcceleratorProgramNodeCatalog.ACCELERATOR).executors(),
                new ProgramExecutionFrame(transaction, runtime)
        );
    }

    private static Map<Identifier, ProgramNodeExecutor<?>> createExecutors() {
        var result = new HashMap<Identifier, ProgramNodeExecutor<?>>();
        put(result, AcceleratorProgramNodeIds.CASTER, (context, _, _) -> data(
                "entity",
                ProgramValueTypes.ENTITY_REFERENCE,
                runtime(context).caster()
        ));
        put(result, AcceleratorProgramNodeIds.LOOK_TARGET, (context, _, _) ->
                runtime(context).lookTarget()
                        .map(value -> data("entity", ProgramValueTypes.ENTITY_REFERENCE, value))
                        .orElseGet(() -> ProgramNodeStep.data(Map.of())));
        put(result, AcceleratorProgramNodeIds.INCOMING_PROJECTILES, (context, _, _) -> data(
                "entities",
                ProgramValueTypes.ENTITY_SET,
                List.copyOf(Objects.requireNonNull(
                        runtime(context).incomingProjectiles(),
                        "Accelerator runtime returned a null projectile set"
                ))
        ));
        put(result, AcceleratorProgramNodeIds.APPLY_VECTOR,
                (ProgramVmContext context,
                 AcceleratorProgramNodeCatalog.StrengthConfiguration configuration,
                 ProgramInputView inputs) -> {
                    var runtime = runtime(context);
                    stage(context, runtime.applyVector(
                            entity(inputs, "entity"),
                            direction(inputs),
                            configuration.tier()
                    ));
                    return ProgramNodeStep.next("flow");
                });
        put(result, AcceleratorProgramNodeIds.KINETIC_IMPACT,
                (ProgramVmContext context,
                 AcceleratorProgramNodeCatalog.StrengthConfiguration configuration,
                 ProgramInputView inputs) -> {
                    var runtime = runtime(context);
                    stage(context, runtime.kineticImpact(
                            entity(inputs, "entity"),
                            direction(inputs),
                            configuration.tier()
                    ));
                    return ProgramNodeStep.next("flow");
                });
        put(result, AcceleratorProgramNodeIds.KINETIC_SHOCKWAVE,
                (ProgramVmContext context,
                 AcceleratorProgramNodeCatalog.ShockwaveConfiguration configuration,
                 ProgramInputView inputs) -> {
                    var runtime = runtime(context);
                    stage(context, runtime.kineticShockwave(
                            worldPosition(inputs, "position"),
                            direction(inputs),
                            configuration.power(),
                            configuration.destroyBlocks(),
                            configuration.radius()
                    ));
                    return ProgramNodeStep.next("flow");
                });
        put(result, AcceleratorProgramNodeIds.REDIRECT_PROJECTILE,
                (context, _, inputs) -> {
                    var runtime = runtime(context);
                    stage(context, runtime.redirectProjectile(
                            entity(inputs, "projectile"),
                            direction(inputs)
                    ));
                    return ProgramNodeStep.next("flow");
                });
        put(result, AcceleratorProgramNodeIds.DISPLACE_ENTITY,
                (ProgramVmContext context,
                 AcceleratorProgramNodeCatalog.StrengthConfiguration configuration,
                 ProgramInputView inputs) -> {
                    var runtime = runtime(context);
                    stage(context, runtime.displaceEntity(
                            entity(inputs, "entity"),
                            worldPosition(inputs, "destination"),
                            configuration.tier()
                    ));
                    return ProgramNodeStep.next("flow");
                });
        put(result, AcceleratorProgramNodeIds.DISPLACE_BLOCK,
                (ProgramVmContext context,
                 AcceleratorProgramNodeCatalog.StrengthConfiguration configuration,
                 ProgramInputView inputs) -> {
                    var runtime = runtime(context);
                    stage(context, runtime.displaceBlock(
                            blockPosition(inputs, "block"),
                            blockPosition(inputs, "destination"),
                            configuration.tier()
                    ));
                    return ProgramNodeStep.next("flow");
                });
        return Map.copyOf(result);
    }

    private static AcceleratorProgramRuntime runtime(ProgramVmContext context) {
        return context.attachment(ProgramExecutionFrame.class)
                .flatMap(frame -> frame.environment(AcceleratorProgramRuntime.class))
                .orElseThrow(() -> new IllegalStateException(
                        "Missing accelerator program runtime"));
    }

    private static void stage(
            ProgramVmContext context,
            ProgramActionTransaction.ProgramAction action
    ) {
        var frame = context.attachment(ProgramExecutionFrame.class).orElseThrow();
        frame.stage(context, Objects.requireNonNull(
                action, "Accelerator runtime returned a null action"));
    }

    private static Object entity(ProgramInputView inputs, String port) {
        return inputs.requireCompatible(port, ProgramValueTypes.ENTITY_REFERENCE).value();
    }

    private static ProgramDirection direction(ProgramInputView inputs) {
        return (ProgramDirection) inputs.requireCompatible(
                "direction", ProgramValueTypes.DIRECTION).value();
    }

    private static ProgramWorldPosition worldPosition(ProgramInputView inputs, String port) {
        return (ProgramWorldPosition) inputs.requireCompatible(
                port, ProgramValueTypes.WORLD_POSITION).value();
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
            throw new IllegalStateException("Duplicate accelerator program executor " + id);
        }
    }

    private static void addLearnedCapability(
            Player data,
            Set<Identifier> capabilities,
            String skillId
    ) {
        if (data != null && data.isSkillLearned(skillId)) {
            capabilities.add(Identifier.parse(skillId));
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
