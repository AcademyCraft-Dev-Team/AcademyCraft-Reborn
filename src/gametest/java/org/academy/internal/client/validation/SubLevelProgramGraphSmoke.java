package org.academy.internal.client.validation;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.structure.*;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.movement.VectorStructureService;
import org.academy.internal.common.ability.aeromanip.movement.HighSpeedJetStructureService;
import org.academy.internal.common.ability.electromaster.magnet.MagneticStructureService;
import org.academy.internal.common.ability.program.AbilityProgramManager;
import org.academy.internal.common.ability.program.ServerProgramScheduler;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;
import org.academy.internal.common.ability.program.registry.*;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.space.NativeStructureDimensions;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.internal.common.sublevel.SubLevelSkillSupport;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle;
import org.misaka.MisakaNetworkClient;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Isolated client fixture: actual save/execute packets, production compiler and server scheduler. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
final class SubLevelProgramGraphSmoke {
    private static final ConcurrentLinkedQueue<Runnable> CLIENT_PACKETS = new ConcurrentLinkedQueue<>();
    private static final Set<UUID> CAPTURED = new HashSet<>();
    private static final List<Parked> PARKED = new ArrayList<>();
    private static UUID parkingTransaction;
    private static BlockPos parkingSource;
    private static volatile boolean started;
    private static boolean previousNoGravity;
    private static Vec3 previousPosition;
    private static float previousYaw;
    private static float previousPitch;
    private static Vec3 arenaPosition;
    private static volatile boolean complete;
    private static int run;
    private static int phase;
    private static int age;
    private static int lastTick = -1;
    private static AbilityProgram program;
    private static ServerProgramScheduler.SessionKey key;
    private static Identifier category;
    private static volatile BlockPos seed;
    private static StructureRef reference;
    private static HighSpeedJetNozzle nozzle;
    private static Vec3 firstCenter;
    private static Vec3 launchOrigin;
    private static double approach;
    private static double freeFlight;
    private static boolean scheduled;
    private static boolean controlled;
    private static boolean rotated;
    private static boolean launched;
    private static int executionWait;

    private SubLevelProgramGraphSmoke() {}

    static boolean complete() { return complete; }

    static void start(MinecraftServer server) {
        if (started) return;
        check(Boolean.getBoolean("academy.sublevelClientSmoke"), "isolated client smoke is required");
        check(!Boolean.getBoolean("academy.sublevelProgramNodes")
                && !Boolean.getBoolean("academy.sublevelDirectSkills"), "production routing flags must be absent");
        check(StructureBackendRouter.nativeEnabled(server), "fixture requires no Sable");
        var player = server.getPlayerList().getPlayers().getFirst();
        previousNoGravity = player.isNoGravity();
        previousPosition = player.position();
        previousYaw = player.getYRot();
        previousPitch = player.getXRot();
        arenaPosition = new Vec3(previousPosition.x, player.level().getMaxY() - 80, previousPosition.z);
        verifyEmptyArena(player);
        placePlayerInArena(player);
        var host = player.level();
        var legacy = NativeStructureRuntime.backend(server);
        int reserved = 0;
        for (int slot = 0; slot < NativeStructureDimensions.SLOT_COUNT; slot++) {
            if (legacy.createEmpty(host, StructurePose.identity(new Vec3(0, host.getMaxY() - 20, 0))).isPresent()) reserved++;
        }
        check(legacy.createEmpty(host, StructurePose.identity(new Vec3(0, host.getMaxY() - 20, 0))).isEmpty(),
                "legacy slots were not exhausted");
        AcademyCraft.LOGGER.info("SUBLEVEL_GRAPH_LEGACY_CAPACITY reserved_now={} no_free_native_slot=true", reserved);
        started = true;
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) return;
        if (started && !complete && minecraft.player != null) {
            minecraft.player.setNoGravity(true);
            minecraft.player.setDeltaMovement(Vec3.ZERO);
            minecraft.player.resetFallDistance();
            var currentSeed = seed;
            if (currentSeed != null) {
                var aim = Vec3.atCenterOf(currentSeed).subtract(minecraft.player.getEyePosition());
                minecraft.player.setYRot((float) Math.toDegrees(Math.atan2(-aim.x, aim.z)));
                minecraft.player.setXRot((float) -Math.toDegrees(Math.atan2(aim.y,
                        Math.sqrt(aim.x * aim.x + aim.z * aim.z))));
                // Post runs after vanilla movement; explicitly publish this view before custom packets.
                minecraft.player.connection.send(new ServerboundMovePlayerPacket.Rot(
                        minecraft.player.getYRot(), minecraft.player.getXRot(), minecraft.player.onGround(),
                        minecraft.player.horizontalCollision));
            }
        }
        for (Runnable send; (send = CLIENT_PACKETS.poll()) != null;) send.run();
    }

    static void tick(MinecraftServer server) {
        if (complete || !started || lastTick == server.getTickCount()) return;
        lastTick = server.getTickCount();
        var player = server.getPlayerList().getPlayers().getFirst();
        check(++age < 600, "packet graph timed out run=" + run + " phase=" + phase);
        if (PARKED.size() < 5) {
            park(player);
            return;
        }
        if (phase == 0) {
            prepare(player);
            phase = 1;
            return;
        }
        if (phase == 1) {
            var stored = AbilityProgramManager.bookForAddon(player).slot(run / 3).program();
            if (stored == null || !stored.id().equals(program.id())) return;
            check(stored.graph().equals(program.graph()), "saved graph changed during packet round trip");
            var towardSeed = Vec3.atCenterOf(seed).subtract(player.position());
            var forwardDistance = towardSeed.dot(Vec3.directionFromRotation(0, player.getYRot()));
            if (forwardDistance < 5) return;
            if (run % 3 == 0) check(VectorStructureService.programCaptureSelection(player, seed, 0, 1).contains(seed),
                    "saved graph seed is not eligible for kinetic capture at execution time");
            AcademyCraft.LOGGER.info("SUBLEVEL_PROGRAM_PACKET_GRAPH_EXECUTE run={} seed={} input={} player={}"
                            + " yaw={} forward_distance={} state={} above_state={} permitted={}",
                    run, seed, stored.graph().nodes().stream().filter(node -> node.id() == 1).findFirst().orElseThrow(),
                    player.position(), player.getYRot(), forwardDistance, player.level().getBlockState(seed),
                    player.level().getBlockState(seed.above()), player.level().mayInteract(player, seed));
            var packet = new AbilityProgramManager.ExecutePacket(category, run / 3, System.currentTimeMillis());
            CLIENT_PACKETS.add(() -> MisakaNetworkClient.send(packet));
            phase = 2;
            return;
        }
        boolean running = ServerProgramScheduler.contains(server, key);
        scheduled |= running;
        check(scheduled || ++executionWait < 40, "execute packet never produced a resumable graph; inspect node failure above");
        var observed = switch (run % 3) {
            case 0 -> VectorStructureService.diagnostic(player).map(state -> state.reference());
            case 1 -> HighSpeedJetStructureService.diagnostic(player).map(state -> state.reference());
            default -> MagneticStructureService.programSession(player).map(state -> state.reference());
        };
        observed.ifPresent(value -> {
            check(SubLevelStructureRefs.isSubLevel(value), "default graph selected legacy backend " + value.backend());
            check(reference == null || reference.equals(value), "retry repeated capture");
            if (reference == null) check(CAPTURED.add(value.id()), "capture reused an earlier identity");
            reference = value;
        });
        if (reference != null) {
            var handle = StructureSpaces.resolve(server, reference).orElse(null);
            var motion = StructureDynamics.motion(player.level(), reference).orElse(null);
            if (handle != null && motion != null) {
                var center = motion.pose().localToWorldPoint(motion.localCenterOfMass());
                if (firstCenter == null) firstCenter = center;
                var target = Vec3.atCenterOf(seed.above(3));
                if (!rotated) approach = Math.max(approach,
                        firstCenter.distanceTo(target) - center.distanceTo(target));
                boolean rotatedNow = motion.pose().localToWorldDirection(new Vec3(1, 0, 0))
                        .distanceTo(new Vec3(0, 1, 0)) < 1.0E-4;
                if (rotatedNow && !rotated) {
                    check(approach > 0.05, "move node did not advance its anchor before rotation: " + approach);
                    rotated = true;
                }
                boolean held = StructureDirectControl.occupied(reference, handle.runtimeId());
                controlled |= held;
                if (rotated && controlled && !held && motion.linearVelocity().y > 4) {
                    if (launchOrigin == null) launchOrigin = center;
                    launched = true;
                }
                if (launchOrigin != null) freeFlight = Math.max(freeFlight, center.distanceTo(launchOrigin));
            }
        }
        if (!scheduled || running) return;
        check(reference != null && controlled, "real graph never acquired structure control");
        check(rotated && launched && freeFlight > 0.1,
                "graph control chain failed rotated=" + rotated + " launched=" + launched + " flight=" + freeFlight);
        check(StructureSpaces.resolve(server, reference).isEmpty(), "staticize left the physical structure");
        check(player.level().getBlockState(seed).is(Blocks.IRON_BLOCK), "staticize did not restore original content");
        AcademyCraft.LOGGER.info("SUBLEVEL_PROGRAM_PACKET_GRAPH_PASSED category={} run={} ref={} saved=true"
                + " actual_packets=true scheduler=true move_approach={} rotation=true launch=true release=true"
                + " free_flight={} staticize=true", category, run, reference, approach, freeFlight);
        if (nozzle != null) nozzle.discard();
        player.level().setBlock(seed, Blocks.AIR.defaultBlockState(), 3);
        VectorStructureService.clear(player);
        HighSpeedJetStructureService.clear(player);
        MagneticStructureService.releasePrograms(player);
        if (++run == 6) {
            check(CAPTURED.size() == 6, "fewer than six independent captures completed");
            var runtime = SubLevelServer.runtime(player.level()).orElseThrow();
            check(PARKED.stream().allMatch(parked -> runtime.get(
                    SubLevelStructureRefs.id(parked.reference()).orElseThrow()).isPresent()),
                    "five parked bodies did not survive all six graphs");
            for (var parked : PARKED) {
                var restored = SubLevelSkillSupport.staticize(server,
                        SubLevelStructureRefs.id(parked.reference()).orElseThrow(), parked.source());
                check(restored.succeeded(), "parked body cleanup failed: " + restored.detail());
                player.level().setBlock(parked.source(), Blocks.AIR.defaultBlockState(), 3);
            }
            player.resetFallDistance();
            resetClientFallDistance();
            check(player.teleportTo(player.level(), previousPosition.x, previousPosition.y, previousPosition.z,
                    Set.of(), previousYaw, previousPitch, false), "cannot restore player after packet graph");
            player.resetFallDistance();
            player.setYHeadRot(previousYaw);
            player.setNoGravity(previousNoGravity);
            player.setDeltaMovement(Vec3.ZERO);
            CLIENT_PACKETS.add(() -> {
                var localPlayer = Minecraft.getInstance().player;
                if (localPlayer != null) {
                    localPlayer.resetFallDistance();
                    localPlayer.setNoGravity(previousNoGravity);
                    localPlayer.setDeltaMovement(Vec3.ZERO);
                    localPlayer.setYRot(previousYaw);
                    localPlayer.setXRot(previousPitch);
                }
            });
            complete = true;
            AcademyCraft.LOGGER.info("SUBLEVEL_PROGRAM_DEFAULT_ROUTE_PASSED graphs=6 categories=3"
                    + " legacy_slots_full=true simultaneous_parked_sublevels=5 flags=false actual_save_execute_packets=true");
        } else {
            phase = 0;
            age = 0;
        }
    }

    private static void park(ServerPlayer player) {
        StructureCaptureResult result;
        if (parkingTransaction == null) {
            parkingSource = player.blockPosition().offset(-12, 10, PARKED.size() * 2);
            player.level().getChunkAt(parkingSource);
            check(player.level().getBlockState(parkingSource).isAir(), "parking source is occupied");
            check(player.level().setBlock(parkingSource, Blocks.IRON_BLOCK.defaultBlockState(), 3),
                    "cannot place parking source");
            result = StructureTransactions.capture(player.level(), new StructureCaptureRequest(
                    List.of(parkingSource), 1, 1, StructureCaptureRequest.ALLOW, true));
        } else {
            result = StructureTransactions.poll(player.level().getServer(), parkingTransaction).orElseThrow();
        }
        if (result.pending()) {
            parkingTransaction = result.transactionId();
            return;
        }
        check(result.succeeded(), "public physical capture failed with full legacy slots: " + result);
        var reference = result.structure().orElseThrow();
        check(SubLevelStructureRefs.isSubLevel(reference), "public physical capture selected " + reference.backend());
        PARKED.add(new Parked(reference, parkingSource));
        parkingTransaction = null;
        if (PARKED.size() == 5) {
            var runtime = SubLevelServer.runtime(player.level()).orElseThrow();
            check(PARKED.stream().allMatch(parked -> runtime.get(
                    SubLevelStructureRefs.id(parked.reference()).orElseThrow()).isPresent()),
                    "five captured SubLevels are not simultaneously present");
            AcademyCraft.LOGGER.info("SUBLEVEL_GRAPH_PARKED_CAPACITY_PASSED legacy_slots_full=true simultaneous_sublevels=5");
            age = 0;
        }
    }

    private record Parked(StructureRef reference, BlockPos source) {}

    private static void verifyEmptyArena(ServerPlayer player) {
        var origin = BlockPos.containing(arenaPosition);
        var minimum = origin.offset(-16, -8, -12);
        var maximum = origin.offset(16, 40, 16);
        for (int chunkX = minimum.getX() >> 4; chunkX <= maximum.getX() >> 4; chunkX++) {
            for (int chunkZ = minimum.getZ() >> 4; chunkZ <= maximum.getZ() >> 4; chunkZ++) {
                player.level().getChunkAt(new BlockPos(chunkX << 4, origin.getY(), chunkZ << 4));
            }
        }
        for (var position : BlockPos.betweenClosed(minimum, maximum)) {
            check(player.level().getBlockState(position).isAir(), "high altitude graph arena is occupied: " + position);
        }
        AcademyCraft.LOGGER.info("SUBLEVEL_PROGRAM_GRAPH_ARENA position={} minimum={} maximum={} clear=true",
                arenaPosition, minimum, maximum);
    }

    private static void placePlayerInArena(ServerPlayer player) {
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        resetClientFallDistance();
        check(player.teleportTo(player.level(), arenaPosition.x, arenaPosition.y, arenaPosition.z,
                Set.of(), player.getYRot(), player.getXRot(), false), "cannot enter high altitude graph arena");
        player.resetFallDistance();
        resetClientFallDistance();
    }

    private static void resetClientFallDistance() {
        CLIENT_PACKETS.add(() -> {
            var localPlayer = Minecraft.getInstance().player;
            if (localPlayer != null) localPlayer.resetFallDistance();
        });
    }

    private static void prepare(ServerPlayer player) {
        placePlayerInArena(player);
        var system = AbilitySystemServer.getSystem(player);
        var ability = switch (run % 3) {
            case 0 -> AbilityCategories.ACCELERATOR.get();
            case 1 -> AbilityCategories.AEROMANIP.get();
            default -> AbilityCategories.ELECTROMASTER.get();
        };
        var skill = switch (run % 3) {
            case 0 -> Skills.KINETIC_THROW.get();
            case 1 -> Skills.HIGH_SPEED_JET.get();
            default -> Skills.MAGNET_MANIPULATION.get();
        };
        system.setPlayerAbilityCategory(player.getUUID(), ability);
        system.setPlayerLevel(player.getUUID(), 5);
        system.setPlayerBaseMaxCP(player.getUUID(), 100_000);
        system.setPlayerAvailableCP(player.getUUID(), 100_000);
        system.setPlayerStatus(player.getUUID(), org.academy.api.common.data.AbilityData.Status.NORMAL);
        system.addPlayerSkill(player, skill.getKeyString());
        if (!skill.isEnabled(player)) system.toggleSkill(player.getUUID(), skill.getKeyString());
        if (run % 3 == 1) {
            system.getAeromanipResourceManager().onPlayerLogin(player);
            system.setPlayerCurrMP(player.getUUID(), system.getAeromanipResourceManager().getCapacity(player));
        }
        player.setGameMode(GameType.SURVIVAL);
        category = ability.getKey();
        seed = player.blockPosition().offset(6, 8, 0);
        player.level().getChunkAt(seed);
        check(player.level().getBlockState(seed).isAir(), "fixture seed is occupied");
        check(player.level().setBlock(seed, Blocks.IRON_BLOCK.defaultBlockState(), 3), "could not place graph seed");
        // Kinetic selection requires the real forward-facing host target.
        var aim = Vec3.atCenterOf(seed).subtract(player.getEyePosition());
        player.setYRot((float) Math.toDegrees(Math.atan2(-aim.x, aim.z)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z))));
        nozzle = null;
        if (run % 3 == 1) {
            nozzle = new HighSpeedJetNozzle(EntityTypes.HIGH_SPEED_JET_NOZZLE.get(), player.level());
            nozzle.attach(player.getUUID(), seed, Direction.DOWN);
            check(player.level().addFreshEntity(nozzle), "could not attach graph nozzle");
        }
        program = new AbilityProgram(AbilityProgram.CURRENT_SCHEMA_VERSION, UUID.randomUUID(),
                "SubLevel packet graph " + run, category, graph(player), ProgramEditorLayout.EMPTY);
        var definition = AbilityProgramDefinitions.require(category);
        var compiled = definition.compile(program, Set.of(skill.getKey()));
        check(compiled.valid(), "graph is not authorable: " + compiled.diagnostics());
        key = new ServerProgramScheduler.SessionKey(player.getUUID(), category, program.id(), run / 3);
        var revision = AbilityProgramManager.bookForAddon(player).revision();
        var save = new AbilityProgramManager.SavePacket(category, run / 3, revision, ProgramBookCodec.encodeProgram(program));
        CLIENT_PACKETS.add(() -> MisakaNetworkClient.send(save));
        reference = null;
        firstCenter = null;
        launchOrigin = null;
        approach = 0;
        freeFlight = 0;
        scheduled = controlled = rotated = launched = false;
        executionWait = 0;
    }

    private static ProgramGraph graph(ServerPlayer player) {
        var definition = AbilityProgramDefinitions.require(category);
        var extract = new JsonObject();
        extract.addProperty("budget", 1);
        if (run % 3 == 0) extract.addProperty("tier", 0);
        else if (run % 3 == 1) extract.addProperty("radius", 2);
        else extract.addProperty("force_magnetize", false);
        var launch = new JsonObject();
        if (run % 3 == 0) launch.addProperty("strength", 1);
        else launch.addProperty("power", 1.0);
        var move = new JsonObject();
        move.addProperty("duration_seconds", 2.0);
        return new ProgramGraph(List.of(
                node(definition, 0, BaseAbilityProgramDefinition.entryId(category), new JsonObject()),
                node(definition, 1, CommonProgramNodeIds.BLOCK_POSITION_CONSTANT, position(player, seed, false)),
                node(definition, 2, action("extract"), extract),
                node(definition, 3, action("control"), new JsonObject()),
                node(definition, 4, action("move"), move),
                node(definition, 5, CommonProgramNodeIds.WORLD_POSITION_CONSTANT, position(player, seed.above(3), true)),
                node(definition, 6, CommonProgramNodeIds.WAIT, new JsonObject()),
                node(definition, 7, CommonProgramNodeIds.INTEGER_CONSTANT, value(12)),
                node(definition, 8, action("rotate"), new JsonObject()),
                node(definition, 9, CommonProgramNodeIds.STRUCTURE_POSE, new JsonObject()),
                node(definition, 10, CommonProgramNodeIds.DIRECTION_CONSTANT, direction(0, 0, 1)),
                node(definition, 11, CommonProgramNodeIds.FLOAT_CONSTANT, value(Math.PI / 2)),
                node(definition, 12, CommonProgramNodeIds.WAIT, new JsonObject()),
                node(definition, 13, CommonProgramNodeIds.INTEGER_CONSTANT, value(2)),
                node(definition, 14, action("launch"), launch),
                node(definition, 15, CommonProgramNodeIds.DIRECTION_CONSTANT, direction(0, 1, 0)),
                node(definition, 16, action("release"), new JsonObject()),
                node(definition, 17, CommonProgramNodeIds.WAIT, new JsonObject()),
                node(definition, 18, CommonProgramNodeIds.INTEGER_CONSTANT, value(4)),
                node(definition, 19, action("staticize"), new JsonObject()),
                node(definition, 20, CommonProgramNodeIds.STRUCTURE_ANCHOR, new JsonObject()),
                node(definition, 21, CommonProgramNodeIds.INTEGER_CONSTANT, value(0))),
                List.of(edge(0, "flow", 2, "flow"), edge(1, "position", 2, "host"),
                        edge(2, "flow", 3, "flow"), edge(2, "structure", 3, "structure"),
                        edge(3, "flow", 4, "flow"), edge(3, "control", 4, "control"),
                        edge(5, "position", 4, "target"), edge(20, "anchor", 4, "anchor"),
                        edge(2, "structure", 20, "structure"), edge(21, "value", 20, "local_x"),
                        edge(21, "value", 20, "local_y"), edge(21, "value", 20, "local_z"),
                        edge(4, "flow", 6, "flow"), edge(7, "value", 6, "ticks"),
                        edge(6, "flow", 8, "flow"), edge(3, "control", 8, "control"),
                        edge(9, "pose", 8, "pose"), edge(10, "direction", 9, "axis"),
                        edge(11, "value", 9, "angle_radians"), edge(8, "flow", 12, "flow"),
                        edge(13, "value", 12, "ticks"), edge(12, "flow", 14, "flow"),
                        edge(3, "control", 14, "control"), edge(15, "direction", 14, "direction"),
                        edge(14, "flow", 16, "flow"), edge(3, "control", 16, "control"),
                        edge(16, "flow", 17, "flow"), edge(18, "value", 17, "ticks"),
                        edge(17, "flow", 19, "flow"), edge(2, "structure", 19, "structure"),
                        edge(1, "position", 19, "target")));
    }

    private static Identifier action(String action) {
        return AcademyCraft.academy("program/" + category.getPath() + "/action/structure/" + action);
    }

    private static JsonObject position(ServerPlayer player, BlockPos position, boolean center) {
        var json = new JsonObject();
        json.addProperty("dimension", player.level().dimension().identifier().toString());
        json.addProperty("x", position.getX() + (center ? 0.5 : 0));
        json.addProperty("y", position.getY() + (center ? 0.5 : 0));
        json.addProperty("z", position.getZ() + (center ? 0.5 : 0));
        return json;
    }

    private static JsonObject value(Number value) {
        var json = new JsonObject();
        json.addProperty("value", value);
        return json;
    }

    private static JsonObject direction(double x, double y, double z) {
        var json = new JsonObject();
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("z", z);
        return json;
    }

    private static ProgramGraph.Node node(AbilityProgramDefinition definition, int id, Identifier type, JsonObject config) {
        return new ProgramGraph.Node(id, type, definition.nodeLookup().find(type).schemaVersion(), config);
    }

    private static ProgramGraph.Edge edge(int from, String output, int to, String input) {
        return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input));
    }

    private static void check(boolean valid, String detail) {
        if (!valid) throw new IllegalStateException("SubLevel packet graph: " + detail);
    }
}
