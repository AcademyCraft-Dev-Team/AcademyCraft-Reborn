package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.structure.*;
import org.academy.api.common.structure.upgrade.StructureCaptureExtractions;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.movement.VectorStructureService;
import org.academy.internal.common.ability.accelerator.skills.lv3.KineticThrow;
import org.academy.internal.common.ability.accelerator.program.ServerAcceleratorProgramRuntime;
import org.academy.internal.common.ability.aeromanip.movement.HighSpeedJetStructureService;
import org.academy.internal.common.ability.aeromanip.program.ServerAeromanipProgramRuntime;
import org.academy.internal.common.ability.electromaster.magnet.MagneticStructureService;
import org.academy.internal.common.ability.electromaster.program.ServerElectromasterProgramRuntime;
import org.academy.internal.common.ability.program.*;
import org.academy.internal.common.ability.program.registry.*;
import org.academy.internal.common.structure.upgrade.control.OrientationTargets;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle;
import org.academy.mixin.common.MixinServerGamePacketListenerNativeStructureAccess;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Production node executors, category runtimes and scheduler with observation-only wrappers. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class StructureProgramOrientationSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.structureProgramOrientationSmoke");
    private static final int SPIN_TICKS = 250;
    private static final int ARENA_READY_TIMEOUT_TICKS = 200;
    private static final double ANGLE_TOLERANCE = 0.02;
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final Map<Integer, Long> STARTED = new HashMap<>();
    private static final List<Integer> COMPLETED = new ArrayList<>();
    private static final StructurePose ROTATION = OrientationTargets.axisAngle(new Vec3(0, 0, 1), -Math.PI / 4);
    private static volatile boolean ready, finished, failed, closing;
    private static Path output;
    private static ServerPlayer player;
    private static Vec3 oldPosition, oldVelocity, hoverCenter;
    private static float oldYaw, oldPitch, aimYaw, aimPitch;
    private static boolean oldNoGravity;
    private static GameType oldMode;
    private static int categoryIndex, age, lastTick = -1;
    private static boolean awaitingArena;
    private static int arenaGeneration, arenaWaitTicks, arenaStableTicks;
    private static volatile ArenaSetup arenaSetup;
    private static volatile int clientArenaReadyGeneration = -1;
    private static Identifier category;
    private static BlockPos seed;
    private static StructureRef reference;
    private static ProgramStructureControl control;
    private static ProgramStructureOrientationRuntime runtime;
    private static ServerProgramScheduler.SessionKey key;
    private static ProgramActionTransaction transaction;
    private static ProgramSessionScheduler.Termination termination;
    private static Throwable settlementFailure;
    private static HighSpeedJetNozzle nozzle;
    private static double rotationError, resetError, lateSpinSpeed;
    private static double actualExtractionRise, expectedExtractionRise;
    private static long extractionWaitTicks, capturePublishedAt;
    private static int spinSamplesAfter200;

    private StructureProgramOrientationSmoke() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || !ready || finished || failed) return;
        var server = event.getServer();
        if (server.getPlayerList().getPlayers().isEmpty() || lastTick == server.getTickCount()) return;
        lastTick = server.getTickCount();
        try {
            if (player == null) {
                player = server.getPlayerList().getPlayers().getFirst();
                oldPosition = player.position(); oldVelocity = player.getDeltaMovement();
                oldYaw = player.getYRot(); oldPitch = player.getXRot();
                oldNoGravity = player.isNoGravity(); oldMode = player.gameMode.getGameModeForPlayer();
                prepare(server);
                return;
            }
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            if (awaitingArena) { awaitArena(server); return; }
            check(++age < 800, "VM graph timed out category=" + category + " nodes=" + COMPLETED);
            if (termination != null) { finishCategory(server); return; }
            if (control != null) holdTranslation();
            if (age % 40 == 0) AcademyCraft.LOGGER.info(
                    "STRUCTURE_PROGRAM_ORIENTATION_PROGRESS category={} backend={} age={} completed={} spin_after_200={} late_speed={}",
                    category, reference == null ? "capture" : reference.backend(), age, COMPLETED, spinSamplesAfter200, lateSpinSpeed);
        } catch (Throwable error) { fail(error); }
    }

    private static void prepare(MinecraftServer server) {
        var system = AbilitySystemServer.getSystem(player);
        var ability = switch (categoryIndex) {
            case 0 -> AbilityCategories.ACCELERATOR.get();
            case 1 -> AbilityCategories.ELECTROMASTER.get();
            default -> AbilityCategories.AEROMANIP.get();
        };
        var skill = switch (categoryIndex) {
            case 0 -> Skills.KINETIC_THROW.get();
            case 1 -> Skills.MAGNET_MANIPULATION.get();
            default -> Skills.HIGH_SPEED_JET.get();
        };
        category = ability.getKey();
        system.setPlayerAbilityCategory(player.getUUID(), ability);
        system.setPlayerLevel(player.getUUID(), 5);
        system.setPlayerBaseMaxCP(player.getUUID(), 100_000);
        system.setPlayerAvailableCP(player.getUUID(), 100_000);
        system.setPlayerStatus(player.getUUID(), org.academy.api.common.data.AbilityData.Status.NORMAL);
        system.addPlayerSkill(player, skill.getKeyString());
        if (!skill.isEnabled(player)) system.toggleSkill(player.getUUID(), skill.getKeyString());
        if (categoryIndex == 2) {
            system.getAeromanipResourceManager().onPlayerLogin(player);
            system.setPlayerCurrMP(player.getUUID(), system.getAeromanipResourceManager().getCapacity(player));
        }
        seed = new BlockPos((int) Math.floor(oldPosition.x) + 6,
                player.level().getMaxY() - 48, (int) Math.floor(oldPosition.z));
        for (var pos : BlockPos.betweenClosed(seed.offset(-8, -6, -6), seed.offset(6, 6, 6))) {
            player.level().getChunkAt(pos);
            check(player.level().getBlockState(pos).isAir(), "isolated arena occupied: " + pos);
        }
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        var position = new Vec3(seed.getX() - 6, seed.getY(), seed.getZ() + 0.5);
        var aim = Vec3.atCenterOf(seed).subtract(position.add(0, player.getEyeHeight(), 0));
        aimYaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
        aimPitch = (float) -Math.toDegrees(Math.atan2(aim.y, Math.hypot(aim.x, aim.z)));
        // The teleport packet must carry the real aim. A later client acknowledgement of
        // yaw=0 can otherwise overwrite a server-only setYRot before the extract node runs.
        player.connection.teleport(position.x, position.y, position.z, aimYaw, aimPitch);
        check(player.level().setBlock(seed, Blocks.IRON_BLOCK.defaultBlockState(), 3), "seed placement failed");
        if (categoryIndex == 2) {
            nozzle = new HighSpeedJetNozzle(EntityTypes.HIGH_SPEED_JET_NOZZLE.get(), player.level());
            nozzle.attach(player.getUUID(), seed, Direction.DOWN);
            check(player.level().addFreshEntity(nozzle), "nozzle placement failed");
        }
        STARTED.clear(); COMPLETED.clear();
        reference = null; control = null; hoverCenter = null; termination = null; settlementFailure = null;
        runtime = null; key = null; transaction = null;
        rotationError = resetError = Double.NaN; lateSpinSpeed = 0; spinSamplesAfter200 = 0; age = 0;
        actualExtractionRise = Double.NaN; extractionWaitTicks = -1; capturePublishedAt = -1;
        arenaWaitTicks = arenaStableTicks = 0;
        awaitingArena = true;
        arenaSetup = new ArenaSetup(++arenaGeneration, position, seed, aimYaw, aimPitch);
        AcademyCraft.LOGGER.info("STRUCTURE_PROGRAM_ORIENTATION_PREPARE category={} seed={} position={} yaw={} pitch={}",
                category, seed, position, aimYaw, aimPitch);
    }

    private static void awaitArena(MinecraftServer server) {
        var setup = arenaSetup;
        var teleportPending = ((MixinServerGamePacketListenerNativeStructureAccess) player.connection)
                .academy$awaitingPositionFromClient() != null;
        check(player.level().getBlockState(seed).is(Blocks.IRON_BLOCK), "seed changed before the VM started");
        boolean serverAligned = aligned(player.position(), player.getYRot(), player.getXRot(), setup);
        boolean clientReady = clientArenaReadyGeneration == setup.generation();
        check(++arenaWaitTicks <= ARENA_READY_TIMEOUT_TICKS,
                "arena initialization timed out: teleport_pending=" + teleportPending + " client_ready=" + clientReady
                        + " server_aligned=" + serverAligned + " position=" + player.position()
                        + " yaw=" + player.getYRot() + " expected_yaw=" + setup.yaw());
        arenaStableTicks = !teleportPending && clientReady && serverAligned ? arenaStableTicks + 1 : 0;
        if (arenaStableTicks < 2) return;
        awaitingArena = false;
        AcademyCraft.LOGGER.info("STRUCTURE_PROGRAM_ORIENTATION_ARENA_READY category={} wait_ticks={} teleport_ack=true client_seed_loaded=true yaw={}",
                category, arenaWaitTicks, player.getYRot());
        startGraph(server);
    }

    private static boolean aligned(Vec3 position, float yaw, float pitch, ArenaSetup setup) {
        return position.distanceToSqr(setup.position()) < 0.0001
                && Math.abs(Math.IEEEremainder(yaw - setup.yaw(), 360.0)) < 0.01
                && Math.abs(pitch - setup.pitch()) < 0.01;
    }

    private static void startGraph(MinecraftServer server) {
        var skill = switch (categoryIndex) {
            case 0 -> Skills.KINETIC_THROW.get();
            case 1 -> Skills.MAGNET_MANIPULATION.get();
            default -> Skills.HIGH_SPEED_JET.get();
        };
        runtime = switch (categoryIndex) {
            case 0 -> new ServerAcceleratorProgramRuntime(player);
            case 1 -> new ServerElectromasterProgramRuntime(player);
            default -> new ServerAeromanipProgramRuntime(player);
        };
        var definition = AbilityProgramDefinitions.require(category);
        var graph = graph(definition);
        var compiled = definition.compile(graph, Set.of(skill.getKey()));
        check(compiled.valid(), "real graph compilation failed: " + compiled.diagnostics());
        key = new ServerProgramScheduler.SessionKey(player.getUUID(), category, UUID.randomUUID(), 0);
        transaction = ProgramActionTransaction.sequential();
        var frame = new ProgramExecutionFrame(transaction, runtime, null, player.level()::getGameTime);
        check(ServerProgramScheduler.start(server, key, compiled.program(),
                id -> observe(id, definition.executors().find(id)), frame, 1_000, 800,
                (_, result) -> {
                    termination = result;
                    try {
                        if (result.kind() == ProgramSessionScheduler.TerminationKind.COMPLETED) {
                            check(transaction.commit().successful(), "transaction commit failed");
                            transaction.release();
                        } else transaction.abort();
                    } catch (RuntimeException failure) {
                        settlementFailure = failure;
                    } finally { runtime.closeOrientationCommands(); }
                }), "scheduler rejected real graph");
        AcademyCraft.LOGGER.info("STRUCTURE_PROGRAM_ORIENTATION_START category={} seed={} real_nodes=true gravity_support_only=true", category, seed);
    }

    @SuppressWarnings("unchecked")
    private static ProgramNodeExecutor<Object> observe(Identifier id, ProgramNodeExecutor<?> executor) {
        if (executor == null) return null;
        var actual = (ProgramNodeExecutor<Object>) executor;
        return (context, configuration, inputs) -> {
            var now = player.level().getGameTime();
            STARTED.putIfAbsent(context.nodeId(), now);
            if (id.equals(action("spin")) && control != null) {
                long elapsed = now - STARTED.get(context.nodeId());
                var omega = motion().angularVelocity();
                if (elapsed > 200 && elapsed < SPIN_TICKS - 5) {
                    check(StructureDirectControl.validate(player.level(), reference, player, control.controlId())
                            == StructureDirectControl.Status.ACTIVE, "lease expired during long spin");
                    check(omega.y < -0.3, "spin stopped or reversed after tick 200: " + omega);
                    lateSpinSpeed = Math.max(lateSpinSpeed, omega.length());
                    spinSamplesAfter200++;
                }
            }
            // Observation delegates every call to the actual registered production executor.
            if (id.equals(action("extract"))) observePublishedCapture(now);
            var step = actual.execute(context, configuration, inputs);
            if (id.equals(action("extract"))) observePublishedCapture(now);
            if (id.equals(action("extract")) && step.outputs().containsKey("structure")) {
                reference = ((ProgramStructureReference) step.outputs().get("structure").value()).reference();
                StructureProvenanceSmokeAssertions.requireSkill(player, reference, AcademyCraft.academy(switch (categoryIndex) {
                    case 0 -> "kinetic_throw";
                    case 1 -> "magnet_manipulation";
                    default -> "high_speed_jet";
                }));
                var state = motion();
                actualExtractionRise = state.pose().localToWorldPoint(state.localCenterOfMass()).y
                        - Vec3.atCenterOf(seed).y;
                extractionWaitTicks = now - STARTED.get(context.nodeId());
                if (StructureCaptureExtractions.enabled()) {
                    check(Math.abs(actualExtractionRise - expectedExtractionRise) <= 0.1,
                            "extract handed off before reaching source-relative target: rise=" + actualExtractionRise
                                    + " expected=" + expectedExtractionRise);
                    check(extractionWaitTicks > 0 && capturePublishedAt >= 0 && now > capturePublishedAt,
                            "extract handed off immediately after capture: wait=" + extractionWaitTicks
                                    + " published_at=" + capturePublishedAt + " now=" + now);
                }
                AcademyCraft.LOGGER.info("STRUCTURE_PROGRAM_EXTRACTION_HANDOFF category={} enabled={} rise={} expected={} wait_ticks={} post_capture_ticks={}",
                        category, StructureCaptureExtractions.enabled(), actualExtractionRise, expectedExtractionRise,
                        extractionWaitTicks, capturePublishedAt < 0 ? -1 : now - capturePublishedAt);
            }
            if (id.equals(action("control")) && step.outputs().containsKey("control")) {
                control = (ProgramStructureControl) step.outputs().get("control").value();
                hoverCenter = motion().pose().localToWorldPoint(motion().localCenterOfMass());
            }
            if (step.directive() == ProgramNodeStep.Directive.CONTINUE && step.outputs().containsKey("result")) {
                var result = (ProgramStructureOperationResult) step.outputs().get("result").value();
                check(result.status() == ProgramStructureOperationResult.Status.COMPLETED, "flow published before completion: " + result);
                COMPLETED.add(context.nodeId());
                if (id.equals(action("rotate"))) {
                    rotationError = angleError(motion().pose(), ROTATION);
                    check(now > STARTED.get(context.nodeId()), "rotate reported immediate success without physics steps");
                    check(rotationError <= ANGLE_TOLERANCE && motion().angularVelocity().length() <= 0.03,
                            "rotate completed outside angular tolerance: " + rotationError);
                } else if (id.equals(action("reset_orientation"))) {
                    resetError = motion().pose().localToWorldDirection(new Vec3(0, 1, 0)).distanceTo(new Vec3(0, 1, 0));
                    check(resetError <= ANGLE_TOLERANCE && motion().angularVelocity().length() <= 0.03,
                            "reset completed before world-up alignment: " + resetError);
                } else if (id.equals(action("spin"))) {
                    check(now - STARTED.get(context.nodeId()) >= SPIN_TICKS, "spin finished before requested duration");
                    check(spinSamplesAfter200 >= 10 && motion().angularVelocity().length() <= 0.03,
                            "long spin lacks late motion or braking evidence");
                }
            }
            return step;
        };
    }

    private static void holdTranslation() {
        var state = motion();
        var center = state.pose().localToWorldPoint(state.localCenterOfMass());
        var gravity = SubLevelStructureRefs.isSubLevel(reference) ? new Vec3(0, -9.81, 0) : SableGravity.current();
        var force = hoverCenter.subtract(center).scale(16).subtract(state.linearVelocity().scale(8))
                .subtract(gravity).scale(state.mass());
        check(force.length() <= StructurePhysicsControl.MAX_FORCE, "support force exceeds public budget");
        check(StructureDirectControl.submit(player.level(), reference, player, control.controlId(),
                new StructurePhysicsControl.Force(state.localCenterOfMass(), force)), "existing lease support rejected");
        check(center.distanceTo(hoverCenter) < 1.0, "linear support could not isolate angular test");
    }

    /** Observe publication without acquiring authority or helping the extraction move. */
    private static void observePublishedCapture(long now) {
        if (capturePublishedAt >= 0) return;
        var observed = switch (categoryIndex) {
            case 0 -> VectorStructureService.diagnostic(player).map(state -> state.reference());
            case 1 -> MagneticStructureService.programSession(player).map(state -> state.reference());
            default -> HighSpeedJetStructureService.diagnostic(player).map(state -> state.reference());
        };
        observed.ifPresent(candidate -> {
            var handle = StructureSpaces.resolve(player.level().getServer(), candidate).orElse(null);
            if (handle != null && handle.lifecycle() == StructureLifecycle.ACTIVE
                    && StructureDynamics.motion(player.level(), candidate).isPresent()
                    && player.level().getBlockState(seed).isAir()) capturePublishedAt = now;
        });
    }

    private static void finishCategory(MinecraftServer server) {
        check(settlementFailure == null, "VM settlement failed: " + settlementFailure);
        check(termination.kind() == ProgramSessionScheduler.TerminationKind.COMPLETED,
                "VM terminated: " + termination + " completed=" + COMPLETED);
        check(COMPLETED.equals(List.of(2, 3, 4, 8, 9, 10)), "node completion order changed: " + COMPLETED);
        check(control != null && !StructureDirectControl.occupied(reference, control.structureRuntimeId()), "release retained control");
        var row = new LinkedHashMap<String, Object>();
        row.put("category", category.toString()); row.put("backend", reference.backend().toString());
        row.put("extraction_gameplay_enabled", StructureCaptureExtractions.enabled());
        row.put("skill_provenance_verified", true);
        row.put("actual_extraction_rise", actualExtractionRise); row.put("expected_extraction_rise", expectedExtractionRise);
        row.put("wait_ticks", extractionWaitTicks);
        row.put("node_completion_order", List.copyOf(COMPLETED)); row.put("rotate_angle_error", rotationError);
        row.put("reset_up_error", resetError); row.put("spin_requested_ticks", SPIN_TICKS);
        row.put("spin_samples_after_200", spinSamplesAfter200); row.put("late_spin_speed", lateSpinSpeed);
        row.put("linear_gravity_support_only", true); row.put("passed", true);
        RESULTS.add(row);
        AcademyCraft.LOGGER.info("STRUCTURE_PROGRAM_ORIENTATION_CASE_PASSED result={}", row);
        cleanupBody();
        if (++categoryIndex < 3) { prepare(server); return; }
        restorePlayer();
        write(true, ""); finished = true;
        AcademyCraft.LOGGER.info("STRUCTURE_PROGRAM_ORIENTATION_SMOKE_PASSED categories=3 real_vm=true spin_ticks={}", SPIN_TICKS);
    }

    private static ProgramGraph graph(AbilityProgramDefinition definition) {
        var position = new JsonObject();
        position.addProperty("dimension", player.level().dimension().identifier().toString());
        position.addProperty("x", seed.getX()); position.addProperty("y", seed.getY()); position.addProperty("z", seed.getZ());
        var extract = new JsonObject(); extract.addProperty("budget", 1);
        if (categoryIndex == 0) extract.addProperty("tier", 0);
        else if (categoryIndex == 1) extract.addProperty("force_magnetize", false);
        else extract.addProperty("radius", 2);
        expectedExtractionRise = categoryIndex == 0 ? KineticThrow.liftHeight(extract.get("tier").getAsInt()) : 1.0;
        var axis = new JsonObject(); axis.addProperty("x", 0); axis.addProperty("y", 0); axis.addProperty("z", 1);
        var angle = new JsonObject(); angle.addProperty("value", -Math.PI / 4);
        var spinAxis = new JsonObject(); spinAxis.addProperty("x", 0); spinAxis.addProperty("y", 1); spinAxis.addProperty("z", 0);
        var reset = new JsonObject(); reset.addProperty("reset_mode", "world_up_keep_yaw"); reset.addProperty("timeout_seconds", 5.0);
        var spin = new JsonObject(); spin.addProperty("frame", "world"); spin.addProperty("angular_speed", -0.8);
        spin.addProperty("duration_seconds", SPIN_TICKS / 20.0); spin.addProperty("finish_mode", "brake");
        return new ProgramGraph(List.of(
                node(definition, 0, BaseAbilityProgramDefinition.entryId(category), new JsonObject()),
                node(definition, 1, CommonProgramNodeIds.BLOCK_POSITION_CONSTANT, position),
                node(definition, 2, action("extract"), extract), node(definition, 3, action("control"), new JsonObject()),
                node(definition, 4, action("rotate"), new JsonObject()),
                node(definition, 5, CommonProgramNodeIds.STRUCTURE_POSE, new JsonObject()),
                node(definition, 6, CommonProgramNodeIds.DIRECTION_CONSTANT, axis),
                node(definition, 7, CommonProgramNodeIds.FLOAT_CONSTANT, angle),
                node(definition, 8, action("reset_orientation"), reset), node(definition, 9, action("spin"), spin),
                node(definition, 10, action("release"), new JsonObject()),
                node(definition, 11, CommonProgramNodeIds.DIRECTION_CONSTANT, spinAxis)), List.of(
                edge(0, "flow", 2, "flow"), edge(1, "position", 2, "host"),
                edge(2, "flow", 3, "flow"), edge(2, "structure", 3, "structure"),
                edge(3, "flow", 4, "flow"), edge(3, "control", 4, "control"),
                edge(6, "direction", 5, "axis"), edge(7, "value", 5, "angle_radians"), edge(5, "pose", 4, "pose"),
                edge(4, "flow", 8, "flow"), edge(3, "control", 8, "control"),
                edge(8, "flow", 9, "flow"), edge(3, "control", 9, "control"), edge(11, "direction", 9, "axis"),
                edge(9, "flow", 10, "flow"), edge(3, "control", 10, "control")));
    }

    private static Identifier action(String action) { return AcademyCraft.academy("program/" + category.getPath() + "/action/structure/" + action); }
    private static ProgramGraph.Node node(AbilityProgramDefinition definition, int id, Identifier type, JsonObject config) {
        return new ProgramGraph.Node(id, type, definition.nodeLookup().find(type).schemaVersion(), config);
    }
    private static ProgramGraph.Edge edge(int from, String output, int to, String input) {
        return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input));
    }
    private static StructureDynamics.State motion() { return StructureDynamics.motion(player.level(), reference).orElseThrow(); }
    private static double angleError(StructurePose actual, StructurePose target) {
        return 2 * Math.acos(Math.min(1, Math.abs(actual.rotation().dot(target.rotation()))));
    }

    private static void cleanupBody() {
        if (runtime != null) runtime.closeOrientationCommands();
        if (reference != null) check(StructureTransactions.discard(player.level().getServer(), reference), "body cleanup failed");
        VectorStructureService.clear(player); HighSpeedJetStructureService.clear(player); MagneticStructureService.releasePrograms(player);
        if (nozzle != null) { nozzle.discard(); nozzle = null; }
        if (seed != null) player.level().setBlock(seed, Blocks.AIR.defaultBlockState(), 3);
        reference = null; control = null;
    }
    private static void restorePlayer() {
        player.resetFallDistance(); player.connection.teleport(oldPosition.x, oldPosition.y, oldPosition.z, oldYaw, oldPitch);
        player.setNoGravity(oldNoGravity); player.setDeltaMovement(oldVelocity); player.setGameMode(oldMode);
    }
    private static void write(boolean passed, String detail) {
        try {
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(
                    Map.of("passed", passed, "cases", RESULTS, "failure", detail, "actual_node_vm", true,
                            "actual_save_execute_packets", false, "linear_gravity_support_only", true,
                            "extraction_gameplay_enabled", StructureCaptureExtractions.enabled())));
        } catch (Exception error) { AcademyCraft.LOGGER.error("Could not save program orientation report", error); }
    }
    private static void fail(Throwable error) {
        if (failed || finished) return;
        failed = true;
        AcademyCraft.LOGGER.error("STRUCTURE_PROGRAM_ORIENTATION_SMOKE_FAILED category={} completed={}", category, COMPLETED, error);
        try {
            if (player != null) {
                if (key != null) ServerProgramScheduler.cancel(player.level().getServer(), key);
                cleanupBody(); restorePlayer();
            }
        } catch (RuntimeException cleanup) { error.addSuppressed(cleanup); }
        write(false, error.toString());
    }
    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || closing) return;
        var client = Minecraft.getInstance();
        if (finished || failed) { closing = true; client.stop(); return; }
        if (client.player == null || client.level == null || client.getConnection() == null) return;
        var directory = client.gameDirectory.toPath().toAbsolutePath().normalize();
        if (!directory.getFileName().toString().startsWith("native-sublevel-direct-smoke")) {
            fail(new IllegalStateException("program orientation probe requires isolated native-sublevel-direct-smoke* directory")); return;
        }
        output = directory.resolve("structure-program-orientation-result.json");
        client.player.setNoGravity(true); client.player.setDeltaMovement(Vec3.ZERO); client.player.resetFallDistance();
        var setup = arenaSetup;
        if (setup != null) {
            // Observe receipt of the actual teleport and seed before pinning the test aim.
            clientArenaReadyGeneration = aligned(client.player.position(), client.player.getYRot(), client.player.getXRot(), setup)
                    && client.level.hasChunkAt(setup.seed()) && client.level.getBlockState(setup.seed()).is(Blocks.IRON_BLOCK)
                    ? setup.generation() : -1;
            client.player.setYRot(setup.yaw()); client.player.setXRot(setup.pitch());
        }
        if (!ready) org.lwjgl.glfw.GLFW.glfwIconifyWindow(client.getWindow().handle());
        ready = true;
    }
    private record ArenaSetup(int generation, Vec3 position, BlockPos seed, float yaw, float pitch) {}
    private static void check(boolean value, String detail) { if (!value) throw new IllegalStateException(detail); }
    private static final class SableGravity {
        static Vec3 current() {
            var vector = dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(player.level());
            return new Vec3(vector.x, vector.y, vector.z);
        }
    }
}
