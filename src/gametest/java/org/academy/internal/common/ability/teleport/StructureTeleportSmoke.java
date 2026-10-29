package org.academy.internal.common.ability.teleport;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramStructureReference;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.api.common.structure.*;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.area.AreaTeleportState;
import org.academy.internal.common.ability.teleport.program.ServerTeleportProgramRuntime;
import org.academy.internal.common.ability.teleport.program.TeleportProgramNodeCatalog;
import org.academy.internal.common.ability.teleport.skills.lv4.QuickLocationTeleport;
import org.academy.internal.common.ability.teleport.skills.lv4.area.AreaTeleportStart;
import org.academy.internal.common.ability.teleport.targeting.TeleportStructures;
import org.academy.internal.common.structure.upgrade.control.TranslationSweep;

import java.nio.file.*;
import java.util.*;

/** Actual live solver, normal quick-teleport skill gate, area transaction and precision runtime. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class StructureTeleportSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.structureTeleportSmoke");
    private static volatile boolean ready, finished, failed, closing;
    private static Path output;
    private static ServerPlayer player;
    private static Vec3 oldPosition;
    private static boolean oldGravity;
    private static GameType oldMode;
    private static BlockPos seed;
    private static UUID capture;
    private static ProgramStructureReference target;
    private static int age;
    private static volatile boolean exercised, clientProjectionReady, clientSnapChecked;
    private static volatile StructurePose expectedClientPose;
    private static final Map<String, Object> RESULTS = new LinkedHashMap<>();

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || !ready || finished || failed || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        try {
            if (player == null) { prepare(event.getServer().getPlayerList().getPlayers().getFirst()); return; }
            player.setNoGravity(true); player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance();
            check(++age < 500, "structure teleport fixture timed out");
            if (exercised) {
                if (!clientSnapChecked) return;
                if (!org.academy.internal.common.ability.teleport.chunk.ChunkStructureSwapSmoke.tick(player, target)) return;
                RESULTS.put("chunk_leap_map_and_pair", org.academy.internal.common.ability.teleport.chunk.ChunkStructureSwapSmoke.results());
                if (!org.academy.internal.common.structure.upgrade.launch.KineticFlightSmoke.tick(player)) return;
                RESULTS.put("kinetic_flights", org.academy.internal.common.structure.upgrade.launch.KineticFlightSmoke.results());
                cleanup(); restore(); finished = true; write(true, ""); return;
            }
            if (age < 15) return;
            if (capture == null) {
                var result = StructureTransactions.capture(player.level(), new StructureCaptureRequest(
                        List.of(seed, seed.offset(1, 0, 0)), 2, 2, StructureCaptureRequest.ALLOW, true)
                        .withSkillCreation(player.getUUID(), AcademyCraft.academy("kinetic_throw")));
                check(result.pending() || result.succeeded(), "capture rejected " + result);
                capture = result.transactionId(); return;
            }
            if (target == null) {
                var result = StructureTransactions.poll(player.level().getServer(), capture).orElseThrow();
                if (result.pending()) return;
                check(result.succeeded(), "capture failed " + result);
                var reference = result.structure().orElseThrow();
                var handle = StructureSpaces.resolve(player.level().getServer(), reference).orElse(null);
                if (handle == null || StructureDynamics.motion(player.level(), reference).isEmpty()) return;
                target = new ProgramStructureReference(reference, handle.runtimeId());
                return;
            }
            if (org.academy.internal.common.sublevel.SubLevelStructureRefs.isSubLevel(target.reference()) && !clientProjectionReady) return;
            exercise();
            expectedClientPose = motion().pose();
            if (!org.academy.internal.common.sublevel.SubLevelStructureRefs.isSubLevel(target.reference())) clientSnapChecked = true;
            exercised = true;
        } catch (Throwable error) { fail(error); }
    }

    private static void prepare(ServerPlayer selected) {
        player = selected; oldPosition = player.position(); oldGravity = player.isNoGravity();
        oldMode = player.gameMode.getGameModeForPlayer();
        var system = AbilitySystemServer.getSystem(player);
        system.setPlayerAbilityCategory(player.getUUID(), AbilityCategories.TELEPORT.get());
        system.setPlayerLevel(player.getUUID(), 5);
        system.setPlayerBaseMaxCP(player.getUUID(), 100_000); system.setPlayerAvailableCP(player.getUUID(), 100_000);
        system.setPlayerStatus(player.getUUID(), org.academy.api.common.data.AbilityData.Status.NORMAL);
        var skill = Skills.QUICK_LOCATION_TELEPORT.get();
        system.addPlayerSkill(player, skill.getKeyString());
        if (!skill.isEnabled(player)) system.toggleSkill(player.getUUID(), skill.getKeyString());
        seed = new BlockPos((int) Math.floor(oldPosition.x) + 8, player.level().getMaxY() - 60, (int) Math.floor(oldPosition.z));
        for (var pos : BlockPos.betweenClosed(seed.offset(-5, -2, -5), seed.offset(30, 15, 15))) {
            player.level().getChunkAt(pos); check(player.level().getBlockState(pos).isAir(), "arena occupied " + pos);
        }
        player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
        player.connection.teleport(seed.getX() - 4, seed.getY(), seed.getZ() + .5, -90, 0);
        for (int dx : List.of(0, 1)) {
            player.level().setBlock(seed.offset(dx, -1, 0), Blocks.STONE.defaultBlockState(), 3);
            player.level().setBlock(seed.offset(dx, 0, 0), Blocks.IRON_BLOCK.defaultBlockState(), 3);
        }
    }

    private static void exercise() throws Exception {
        var before = motion(); var center = center(before); var destination = center.add(0, 4, 4);
        var initialSummary = handle().world().summarize(4096).orElseThrow();
        check(initialSummary.counts().values().stream().mapToInt(Integer::intValue).sum() == 2, "fixture is not a whole two-block body");
        var request = new StructureTeleports.Request(target.reference(), target.runtimeId(), destination, null);
        check(StructureTeleports.check(player.level(), player,
                new StructureTeleports.Request(target.reference(), UUID.randomUUID(), destination, null)) == StructureTeleports.Result.UNAVAILABLE,
                "stale activation accepted");
        var token = UUID.randomUUID();
        check(StructureDirectControl.acquire(player.level(), target.reference(), player, token) == StructureDirectControl.Status.ACTIVE, "lease setup failed");
        check(StructureTeleports.teleport(player.level(), player, request) == StructureTeleports.Result.OCCUPIED, "teleport stole an existing lease");
        StructureDirectControl.release(target.reference(), token);
        var blocker = BlockPos.containing(destination);
        player.level().setBlock(blocker, Blocks.BEDROCK.defaultBlockState(), 3);
        check(StructureTeleports.teleport(player.level(), player, request) == StructureTeleports.Result.BLOCKED, "teleport entered solid terrain");
        check(center(motion()).distanceTo(center) < 1E-5, "failed admission moved the source");
        player.level().setBlock(blocker, Blocks.AIR.defaultBlockState(), 3);
        RESULTS.put("stale_busy_solid_destination_refused", true);

        var hit = StructureQueries.raycast(player.level(), player.getEyePosition(), center).orElseThrow();
        player.setYRot(90); player.setXRot(20);
        check(QuickLocationTeleport.Server.teleportStructure(player, hit, player.level(), destination, true), "normal quick teleport skill rejected whole body");
        check(center(motion()).distanceTo(destination) < .002, "quick teleport did not move COM");
        check(angle(motion().pose(), TeleportStructures.facing(90, 20)) < 1E-4, "quick teleport direction not applied");
        check(initialSummary.counts().equals(handle().world().summarize(4096).orElseThrow().counts()), "quick teleport changed content");
        RESULTS.put("quick_skill_whole_body_and_direction", true);

        var system = AbilitySystemServer.getSystem(player); var synergy = Skills.SPATIAL_SYNERGY.get();
        system.addPlayerSkill(player, synergy.getKeyString());
        system.setPlayerSkillProficiency(player.getUUID(), synergy, 3000);
        if (!synergy.isEnabled(player)) system.toggleSkill(player.getUUID(), synergy.getKeyString());
        check(synergy.getEffectiveProficiencyMilestone(player) >= 3, "synergy fixture lacks the extra-target milestone");
        var synergyCenter = center(motion());
        player.connection.teleport(synergyCenter.x + 2, synergyCenter.y - 1, synergyCenter.z, 90, 20);
        org.academy.internal.common.ability.teleport.skills.lv2.SpatialSynergy.Server.teleportNearbyTeam(
                player, player.level(), player.position().add(0, 0, 4));
        check(center(motion()).distanceTo(synergyCenter.add(0, 0, 4)) < .002, "owned skill structure did not accompany spatial synergy");
        if (synergy.isEnabled(player)) system.toggleSkill(player.getUUID(), synergy.getKeyString());
        RESULTS.put("spatial_synergy_owned_skill_structure_at_milestone_three", true);

        var runtime = new ServerTeleportProgramRuntime(player);
        var precisionDestination = center(motion()).add(4, 1, 0);
        var action = runtime.teleportEntity(target,
                new ProgramWorldPosition(player.level().dimension().identifier(), precisionDestination.x, precisionDestination.y, precisionDestination.z),
                new ProgramDirection(0, 0, 1), 1, TeleportProgramNodeCatalog.TargetType.STRUCTURE);
        action.validate(); action.apply();
        check(center(motion()).distanceTo(precisionDestination) < .002, "precision structure destination missed");
        check(angle(motion().pose(), StructurePose.identity(Vec3.ZERO)) < 1E-4, "precision direction not applied");
        RESULTS.put("precision_runtime_direction", true);

        var bound = handleBounds();
        var min = BlockPos.containing(bound.minX - 1, bound.minY - 1, bound.minZ - 1);
        var max = BlockPos.containing(bound.maxX + 1, bound.maxY + 1, bound.maxZ + 1);
        var source = new AreaTeleportState.Region(player.level().dimension(), min, max);
        var dest = new AreaTeleportState.Region(player.level().dimension(), min.offset(12, 0, 0),
                min.offset(12 + source.sizeZ() - 1, source.sizeY() - 1, source.sizeX() - 1));
        var original = motion().pose();
        check(!AreaTeleportStart.Server.move(player.level(), player, source, dest, AreaTeleportState.Transform.MIRROR_X, false, false),
                "mirroring rigid structure was allowed");
        check(angle(motion().pose(), original) < 1E-6 && center(motion()).distanceTo(precisionDestination) < .002,
                "rejected mirror changed source");
        check(AreaTeleportStart.Server.move(player.level(), player, source, dest, AreaTeleportState.Transform.ROTATE_90, false, false),
                "area transaction rejected physical body");
        var expectedCenter = new Vec3(dest.min().getX() + source.sizeZ() - (precisionDestination.z - min.getZ()),
                dest.min().getY() + precisionDestination.y - min.getY(), dest.min().getZ() + precisionDestination.x - min.getX());
        check(center(motion()).distanceTo(expectedCenter) < .002, "area COM transform mismatch");
        check(angle(motion().pose(), new StructurePose(Vec3.ZERO, new org.joml.Quaterniond().rotationY(-Math.PI / 2))) < 1E-4,
                "area orientation did not follow spatial rotation");
        check(initialSummary.counts().equals(handle().world().summarize(4096).orElseThrow().counts()), "area teleport copied or deleted structure contents");
        check(!StructureDirectControl.occupied(target.reference(), target.runtimeId()), "teleport leaked control lease");
        RESULTS.put("area_transaction_whole_body_rotation_and_mirror_refusal", true);
        RESULTS.put("backend", target.reference().backend().toString());
        RESULTS.put("identity_and_content_preserved", true);
        RESULTS.put("network_save_execute_packets", false);
        AcademyCraft.LOGGER.info("STRUCTURE_TELEPORT_PASSED {}", RESULTS);
    }

    private static StructureHandle handle() { return StructureSpaces.resolve(player.level().getServer(), target.reference()).filter(target::matches).orElseThrow(); }
    private static StructureDynamics.State motion() { return StructureDynamics.motion(player.level(), target.reference()).orElseThrow(); }
    private static Vec3 center(StructureDynamics.State state) { return state.pose().localToWorldPoint(state.localCenterOfMass()); }
    private static double angle(StructurePose a, StructurePose b) { return 2 * Math.acos(Math.min(1, Math.abs(a.rotation().dot(b.rotation())))); }
    private static AABB handleBounds() {
        var body = org.academy.internal.common.structure.StructureBackendRouter.orientationAccess(player.level(), target.reference())
                .orElseThrow().observe(player.level(), target.reference()).orElseThrow();
        return body.localBoxes().stream().map(box -> TranslationSweep.Box.of(box, body.motion().pose()).bounds()).reduce(AABB::minmax).orElseThrow();
    }
    private static void cleanup() {
        org.academy.internal.common.ability.teleport.chunk.ChunkStructureSwapSmoke.cleanup(player);
        org.academy.internal.common.structure.upgrade.launch.KineticFlightSmoke.cleanup(player);
        if (target != null && StructureSpaces.resolve(player.level().getServer(), target.reference()).isPresent()) {
            check(StructureTransactions.discard(player.level().getServer(), target.reference()), "fixture discard failed");
        }
        for (int dx : List.of(0, 1)) {
            player.level().setBlock(seed.offset(dx, -1, 0), Blocks.AIR.defaultBlockState(), 3);
            player.level().setBlock(seed.offset(dx, 0, 0), Blocks.AIR.defaultBlockState(), 3);
        }
    }
    private static void restore() { player.connection.teleport(oldPosition.x, oldPosition.y, oldPosition.z, 0, 0); player.setNoGravity(oldGravity); player.setGameMode(oldMode); }
    private static void fail(Throwable error) {
        failed = true; AcademyCraft.LOGGER.error("STRUCTURE_TELEPORT_FAILED", error);
        try { if (player != null) { cleanup(); restore(); } } catch (Throwable cleanup) { error.addSuppressed(cleanup); }
        if (output != null) write(false, error.toString());
    }
    private static void write(boolean passed, String failure) {
        try { Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed", passed, "failure", failure, "checks", RESULTS))); }
        catch (Exception error) { AcademyCraft.LOGGER.error("Could not write teleport result", error); }
    }
    @SubscribeEvent public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || closing) return;
        var client = Minecraft.getInstance();
        if (finished || failed) { closing = true; client.stop(); return; }
        if (client.player == null || client.level == null) return;
        var directory = client.gameDirectory.toPath().toAbsolutePath().normalize();
        if (!directory.getFileName().toString().startsWith("native-sublevel-direct-smoke")) {
            fail(new IllegalStateException("Structure teleport requires an isolated run directory")); return;
        }
        output = directory.resolve("structure-teleport-result.json");
        client.player.setNoGravity(true); client.player.setDeltaMovement(Vec3.ZERO);
        if (target != null && org.academy.internal.common.sublevel.SubLevelStructureRefs.isSubLevel(target.reference())) {
            for (var entity : client.level.entitiesForRendering()) {
                if (!(entity instanceof org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity projection)) continue;
                var id = org.academy.internal.client.sublevel.SubLevelClientState.subLevelOf(projection);
                if (id == null || !id.value().equals(target.reference().id())) continue;
                clientProjectionReady = true;
                var wanted = expectedClientPose;
                var observation = org.academy.internal.client.sublevel.SubLevelClientState.teleportObservation(client.level, id).orElse(null);
                if (exercised && wanted != null && !clientSnapChecked && observation != null
                        && observation.targetPose().worldOrigin().distanceTo(wanted.worldOrigin()) < 1) {
                    try {
                        for (var sample : List.of(observation.contactPose(), observation.render0(), observation.renderHalf(), observation.render1())) {
                            check(sample.worldOrigin().distanceTo(observation.targetPose().worldOrigin()) < 1E-8
                                            && angle(sample, observation.targetPose()) < 1E-7,
                                    "client render/contact was not reset in the actual teleport packet callback");
                        }
                        RESULTS.put("native_network_client_teleport_snap", true);
                        RESULTS.put("native_network_teleport_packet_count", observation.count());
                        clientSnapChecked = true;
                    } catch (Throwable error) { fail(error); }
                }
            }
        }
        if (!ready) org.lwjgl.glfw.GLFW.glfwIconifyWindow(client.getWindow().handle());
        ready = true;
    }
    private static void check(boolean condition, String detail) { if (!condition) throw new IllegalStateException(detail); }
}
