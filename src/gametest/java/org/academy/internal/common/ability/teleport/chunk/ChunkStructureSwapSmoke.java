package org.academy.internal.common.ability.teleport.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.program.ProgramStructureReference;
import org.academy.api.common.structure.*;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.map.ChunkMapViewService;
import org.academy.internal.common.ability.teleport.targeting.TeleportStructures;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.upgrade.control.TranslationSweep;

import java.util.*;

/** Actual map request service and queued entity-teleport commit, then the real atomic chunk swap. */
public final class ChunkStructureSwapSmoke {
    private static final Map<String, Object> RESULTS = new LinkedHashMap<>();
    private static int phase, age;
    private static ChunkPos landingChunk;
    private static BlockPos platform;
    private static StructurePose expectedRotation;
    private static StructureBlockSummary summary;
    private static java.util.function.Consumer<StructureTeleportedEvent> listener;
    private static StructureTeleportedEvent.Relocation mapRelocation;
    private ChunkStructureSwapSmoke() {}

    public static boolean tick(ServerPlayer player, ProgramStructureReference target) {
        check(++age < 300, "map structure fixture timed out phase=" + phase);
        var host = player.level();
        if (phase == 0) {
            var system = AbilitySystemServer.getSystem(player); var skill = Skills.CHUNK_LEAP.get();
            system.addPlayerSkill(player, skill.getKeyString());
            if (!skill.isEnabled(player)) system.toggleSkill(player.getUUID(), skill.getKeyString());
            listener = event -> event.relocations().stream().filter(move -> move.reference().equals(target.reference())
                    && move.runtimeId().equals(target.runtimeId())).findFirst().ifPresent(move -> mapRelocation = move);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
            var body = motion(player, target); var center = center(body);
            summary = handle(player, target).world().summarize(4096).orElseThrow();
            landingChunk = new ChunkPos((int) Math.floor(center.x) >> 4, (int) Math.floor(center.z) >> 4);
            platform = new BlockPos(landingChunk.getMinBlockX() + 8, host.getMaxY() - 25, landingChunk.getMinBlockZ() + 8);
            for (var position : BlockPos.betweenClosed(platform.offset(-3, 0, -3), platform.offset(3, 0, 3))) {
                host.getChunkAt(position); check(host.getBlockState(position).isAir(), "map platform occupied");
                host.setBlock(position, Blocks.STONE.defaultBlockState(), 3);
            }
            ChunkMapViewService.onViewRequest(player, new ChunkLeapPackets.ViewRequestPacket(
                    ChunkLeapRegion.ofChunks(host.dimension(), landingChunk.x(), landingChunk.z(), 1, 1), 1, 5));
            phase = 1; return false;
        }
        if (phase == 1) {
            if (!ChunkMapViewService.advertisedStructure(player, target)) return false;
            float cp = AbilitySystemServer.getSystem(player).getPlayerAvailableCP(player.getUUID());
            var old = center(motion(player, target));
            ChunkSwapService.onEntityTeleport(player, new ChunkLeapPackets.EntityTeleportPacket(0,
                    host.dimension().identifier().toString(), platform.getX(), 0, platform.getZ(),
                    new ProgramStructureReference(target.reference(), UUID.randomUUID()), true));
            check(center(motion(player, target)).distanceTo(old) < .001, "stale map runtime changed the body");
            check(cp == AbilitySystemServer.getSystem(player).getPlayerAvailableCP(player.getUUID()), "stale map advertisement charged CP");
            expectedRotation = TeleportStructures.facing(player.getYRot(), player.getXRot());
            ChunkSwapService.onEntityTeleport(player, new ChunkLeapPackets.EntityTeleportPacket(0,
                    host.dimension().identifier().toString(), platform.getX(), 0, platform.getZ(), target, true));
            RESULTS.put("stale_advertised_activation_refused_without_charge", true);
            phase = 2; return false;
        }
        if (phase == 2) {
            if (mapRelocation == null) return false;
            var body = StructureBackendRouter.orientationAccess(host, target.reference()).orElseThrow().observe(host, target.reference()).orElseThrow();
            var bounds = body.localBoxes().stream().map(box -> TranslationSweep.Box.of(box, mapRelocation.destinationPose()).bounds())
                    .reduce(AABB::minmax).orElseThrow();
            check(Math.abs(bounds.minY - (platform.getY() + 1.01)) < 1E-7, "map landing put the rigid body inside terrain: " + bounds);
            check(angle(mapRelocation.destinationPose(), expectedRotation) < 1E-7, "map structure direction was ignored");
            check(motion(player, target).pose().worldOrigin().distanceTo(mapRelocation.destinationPose().worldOrigin()) < 1,
                    "map solver body did not reach its committed relocation");
            check(summary.counts().equals(handle(player, target).world().summarize(4096).orElseThrow().counts()), "map changed body contents");
            RESULTS.put("actual_map_queued_commit_com_direction_loaded_geometry", true);
            RESULTS.put("map_lowest_collision_point_above_surface", true);
            RESULTS.put("actual_network_packet_roundtrip", false);
            pair(player, target);
            cleanup(player);
            phase = 3; return true;
        }
        return true;
    }

    private static void pair(ServerPlayer player, ProgramStructureReference target) {
        var host = player.level(); var point = center(motion(player, target));
        var from = new ChunkPos((int) Math.floor(point.x) >> 4, (int) Math.floor(point.z) >> 4);
        var to = new ChunkPos(from.x() + 3, from.z());
        for (int x = from.x() - 1; x <= to.x() + 1; x++) for (int z = from.z() - 1; z <= from.z() + 1; z++) host.getChunk(x, z);
        var before = center(motion(player, target));
        ChunkRegionSwap.apply(host, host.getChunk(from.x(), from.z()), host.getChunk(to.x(), to.z()),
                ChunkLeapRegion.ofChunks(host.dimension(), from.x(), from.z(), 1, 1),
                ChunkLeapRegion.ofChunks(host.dimension(), to.x(), to.z(), 1, 1), false, player);
        check(center(motion(player, target)).distanceTo(before.add(48, 0, 0)) < .003, "chunk pair left the physical structure behind");
        check(summary.counts().equals(handle(player, target).world().summarize(4096).orElseThrow().counts()), "chunk pair changed structure contents");
        // A body across the pair boundary must be refused before section or BE exchange.
        var edge = new Vec3(to.getMinBlockX() + .1, center(motion(player, target)).y, to.getMinBlockZ() + 8);
        check(StructureTeleports.teleport(host, player, new StructureTeleports.Request(target.reference(), target.runtimeId(), edge, null))
                == StructureTeleports.Result.TELEPORTED, "partial-boundary setup failed");
        var sourceChunk = host.getChunk(to.x(), to.z()); var destinationChunk = host.getChunk(from.x(), from.z());
        var sourceSections = sourceChunk.getSections().clone(); var destinationSections = destinationChunk.getSections().clone();
        boolean refused = false;
        try { ChunkRegionSwap.apply(host, sourceChunk, destinationChunk,
                ChunkLeapRegion.ofChunks(host.dimension(), to.x(), to.z(), 1, 1),
                ChunkLeapRegion.ofChunks(host.dimension(), from.x(), from.z(), 1, 1), false, player); }
        catch (IllegalArgumentException expected) { refused = true; }
        check(refused, "partial physical body accepted by chunk pair");
        check(Arrays.equals(sourceSections, sourceChunk.getSections()) && Arrays.equals(destinationSections, destinationChunk.getSections()),
                "partial refusal changed world section ownership");
        RESULTS.put("real_chunk_pair_whole_body_identity_and_content", true);
        RESULTS.put("partial_pair_refusal_keeps_section_arrays", true);
    }

    public static Map<String,Object> results() { return Map.copyOf(RESULTS); }
    public static void cleanup(ServerPlayer player) {
        if (listener != null) { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener); listener = null; }
        ChunkMapViewService.releasePlayer(player);
        if (platform == null) return;
        // The successful pair carried the platform +48 X; clean only the known fixture image.
        for (int shift : List.of(0, 48)) for (var position : BlockPos.betweenClosed(platform.offset(-3 + shift, 0, -3), platform.offset(3 + shift, 0, 3))) {
            if (player.level().getBlockState(position).is(Blocks.STONE)) player.level().setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        }
    }
    private static StructureHandle handle(ServerPlayer player, ProgramStructureReference target) {
        return StructureSpaces.resolve(player.level().getServer(), target.reference()).filter(target::matches).orElseThrow();
    }
    private static StructureDynamics.State motion(ServerPlayer player, ProgramStructureReference target) { return StructureDynamics.motion(player.level(), target.reference()).orElseThrow(); }
    private static Vec3 center(StructureDynamics.State state) { return state.pose().localToWorldPoint(state.localCenterOfMass()); }
    private static AABB bounds(ServerPlayer player, ProgramStructureReference target) {
        var body = StructureBackendRouter.orientationAccess(player.level(), target.reference()).orElseThrow().observe(player.level(), target.reference()).orElseThrow();
        return body.localBoxes().stream().map(box -> TranslationSweep.Box.of(box, body.motion().pose()).bounds()).reduce(AABB::minmax).orElseThrow();
    }
    private static double angle(StructurePose a, StructurePose b) { return 2 * Math.acos(Math.min(1, Math.abs(a.rotation().dot(b.rotation())))); }
    private static void check(boolean value, String detail) { if (!value) throw new IllegalStateException(detail); }
}
