package org.academy.internal.common.ability.accelerator.program;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.program.*;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.program.*;
import org.academy.internal.common.ability.program.compile.CompiledProgram;
import org.academy.internal.common.ability.program.compile.ProgramCompileResult;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.ProgramExecutorLookup;
import org.academy.internal.common.ability.program.registry.ProgramNodeExecutor;
import org.academy.internal.server.world.level.storage.Player;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.LongSupplier;

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
        addLearnedCapability(data, capabilities, Skills.KINETIC_THROW.get().getKeyString());
        return AbilityProgramDefinitions.require(AcceleratorProgramNodeCatalog.ACCELERATOR)
                .compile(graph, Set.copyOf(capabilities));
    }

    /**
     * Executes a compiled program and settles action nodes in flow order on the server.
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
                AcceleratorProgramNodeCatalog.ACCELERATOR,
                MAX_FUEL,
                AbilityProgramDefinitions.require(
                        AcceleratorProgramNodeCatalog.ACCELERATOR).executors(),
                new ProgramExecutionFrame(
                        transaction,
                        new ServerAcceleratorProgramRuntime(player, costMultiplier),
                        invocation,
                        player.level()::getGameTime
                ),
                transaction,
                invocation
        );
        return new ServerExecutionResult(
                execution.vmResult(), execution.transactionResult());
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
        return execute(program, gameTime, runtime, transaction, null, null);
    }

    private static ProgramVmResult execute(
            CompiledProgram program,
            long gameTime,
            AcceleratorProgramRuntime runtime,
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
                        AcceleratorProgramNodeCatalog.ACCELERATOR).executors(),
                new ProgramExecutionFrame(transaction, runtime, invocation, worldGameTime)
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
        put(result, AcceleratorProgramNodeIds.OBSERVATION_INVERSE, (_, _, inputs) -> {
            var correction = vector(inputs, "expected").subtract(vector(inputs, "observed"));
            return ProgramNodeStep.data(Map.of(
                    "correction", new ProgramValue<>(ProgramValueTypes.VECTOR, correction),
                    "magnitude", new ProgramValue<>(ProgramValueTypes.FLOAT, correction.length())
            ));
        });
        put(result, AcceleratorProgramNodeIds.VECTOR_REFLECTION, (_, _, inputs) -> {
            var incident = vector(inputs, "incident");
            var normal = direction(inputs, "normal");
            var normalVector = ProgramVector.of(normal);
            var reflected = incident.subtract(normalVector.scale(
                    2.0 * incident.dot(normalVector)));
            return data("reflected", ProgramValueTypes.VECTOR, reflected);
        });
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
        put(result, AcceleratorProgramNodeIds.REWRITE_MOTION,
                (ProgramVmContext context,
                 AcceleratorProgramNodeCatalog.PowerConfiguration configuration,
                 ProgramInputView inputs) -> {
                    stage(context, runtime(context).rewriteMotion(
                            entity(inputs, "entity"),
                            vector(inputs, "motion"),
                            configuration.power()
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
        put(result, AcceleratorProgramNodeIds.STRUCTURE_EXTRACT,
                (ProgramVmContext context,
                 AcceleratorProgramNodeCatalog.ExtractStructureConfiguration configuration,
                 ProgramInputView inputs) -> structureResult(
                        runtime(context).extractStructure(
                                blockPosition(inputs, "host"),
                                configuration.tier(),
                                configuration.budget()),
                        true,
                        false));
        put(result, AcceleratorProgramNodeIds.STRUCTURE_CONTROL,
                (context, _, inputs) -> structureResult(
                        runtime(context).controlStructure(
                                structureReference(inputs, "structure")),
                        false,
                        true));
        put(result, AcceleratorProgramNodeIds.STRUCTURE_MOVE,
                (ProgramVmContext context,
                 AcceleratorProgramNodeCatalog.MoveStructureConfiguration configuration,
                 ProgramInputView inputs) -> structureResult(
                        runtime(context).moveStructure(
                                structureControl(inputs),
                                optionalWorldPosition(inputs, "target"),
                                optionalStructureAnchor(inputs, "anchor"),
                                optionalFloat(inputs, "seconds", configuration.durationSeconds())),
                        false,
                        false));
        put(result, AcceleratorProgramNodeIds.STRUCTURE_RESET_ORIENTATION,
                org.academy.internal.common.ability.program.ProgramStructureOrientationExecutors::reset);
        put(result, AcceleratorProgramNodeIds.STRUCTURE_SPIN,
                org.academy.internal.common.ability.program.ProgramStructureOrientationExecutors::spin);
        put(result, AcceleratorProgramNodeIds.STRUCTURE_ROTATE,
                org.academy.internal.common.ability.program.ProgramStructureOrientationExecutors::rotate);
        put(result, AcceleratorProgramNodeIds.STRUCTURE_LAUNCH,
                (ProgramVmContext context,
                 AcceleratorProgramNodeCatalog.StrengthConfiguration configuration,
                 ProgramInputView inputs) -> structureResult(
                        runtime(context).launchStructure(
                                structureControl(inputs),
                                direction(inputs),
                                configuration.tier()),
                        false,
                        false));
        put(result, AcceleratorProgramNodeIds.STRUCTURE_RELEASE,
                (context, _, inputs) -> structureResult(
                        runtime(context).releaseStructure(structureControl(inputs)),
                        false,
                        false));
        put(result, AcceleratorProgramNodeIds.STRUCTURE_STATICIZE,
                (context, _, inputs) -> structureResult(
                        runtime(context).staticizeStructure(
                                structureReference(inputs, "structure"),
                                optionalBlockPosition(inputs, "target")),
                        false,
                        false));
        return Map.copyOf(result);
    }

    private static ProgramNodeStep structureResult(
            AcceleratorProgramRuntime.StructureOperation operation,
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
        return direction(inputs, "direction");
    }

    private static ProgramDirection direction(ProgramInputView inputs, String port) {
        return (ProgramDirection) inputs.requireCompatible(
                port, ProgramValueTypes.DIRECTION).value();
    }

    private static ProgramVector vector(ProgramInputView inputs, String port) {
        return (ProgramVector) inputs.requireCompatible(
                port, ProgramValueTypes.VECTOR).value();
    }

    private static ProgramWorldPosition worldPosition(ProgramInputView inputs, String port) {
        return (ProgramWorldPosition) inputs.requireCompatible(
                port, ProgramValueTypes.WORLD_POSITION).value();
    }

    private static ProgramBlockPosition blockPosition(ProgramInputView inputs, String port) {
        return (ProgramBlockPosition) inputs.requireCompatible(
                port, ProgramValueTypes.BLOCK_POSITION).value();
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
            return vmResult.status() == ProgramVmResult.Status.SUSPENDED
                    || vmResult.status() == ProgramVmResult.Status.FUEL_EXHAUSTED
                    || vmResult.status() == ProgramVmResult.Status.COMPLETED
                    && transactionResult.map(ProgramActionTransaction.Result::successful)
                    .orElse(false);
        }
    }
}
