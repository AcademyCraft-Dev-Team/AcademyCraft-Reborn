package org.academy.internal.common.ability.teleport;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Atomic swap of one chunk pair for 区块跃迁, same-dimension or across dimensions.
 *
 * <p>Whole {@link LevelChunkSection} objects carry the block and biome payload. Within one level the
 * section arrays are exchanged by reference — O(sections), never block by block, because a chunk holds
 * 65536 blocks and calling {@code setBlockState} per block would fire that many neighbour updates and
 * client packets. Across levels the arrays have different vertical layouts, so the overlapping
 * absolute-Y band is exchanged through a byte round-trip instead (see {@link ChunkVerticalBand}).
 *
 * <p>Moving block payloads is only the first half. Block entities, entity positions, heightmaps,
 * sky-light columns and lighting all refer either to the old coordinates or the old neighbours, so each
 * is relocated or rebuilt explicitly. Each chunk is then re-sent as a single whole-chunk packet rather
 * than a per-block storm.
 */
final class ChunkRegionSwap {
    private ChunkRegionSwap() {
    }

    /** Outcome of a pair swap. */
    /**
     * Outcome of a pair swap.
     *
     * <p>Carries the moved players themselves, not just a count: a player who rode a chunk into new terrain
     * needs its client resynced afterwards, and only the swap knows who that was.
     */
    record Result(int entitiesMoved, List<ServerPlayer> playersMoved) {
        boolean movedPlayers() {
            return !playersMoved.isEmpty();
        }
    }

    // ---------------------------------------------------------------- same dimension

    /**
     * Swaps the contents of two chunks in the same level: every section moves.
     *
     * @param movePlayers when true, players standing inside either chunk are moved with it
     */
    static Result apply(ServerLevel level, LevelChunk chunkA, LevelChunk chunkB,
                        ChunkLeapRegion regionA, ChunkLeapRegion regionB, boolean movePlayers) {
        if (chunkA == chunkB) return new Result(0, List.of());
        var deltaAtoB = new BlockPos(
                (regionB.minChunkX() - regionA.minChunkX()) << 4, 0,
                (regionB.minChunkZ() - regionA.minChunkZ()) << 4);
        return run(level, chunkA, level, chunkB, deltaAtoB, ChunkVerticalBand.full(level), movePlayers,
                ChunkRegionSwap::swapSectionArrays);
    }

    /**
     * Swaps the overlapping absolute-Y band of two chunks in different levels.
     *
     * <p>Sections outside the band have no counterpart in the other dimension, so they keep their
     * original content. Because the band is a property of the two levels rather than of the chunk
     * contents, this is still self-inverse: swapping the same pair twice restores both chunks.
     */
    static Result applyAcrossLevels(ServerLevel levelA, LevelChunk chunkA,
                                    ServerLevel levelB, LevelChunk chunkB,
                                    ChunkLeapRegion regionA, ChunkLeapRegion regionB, boolean movePlayers) {
        var band = ChunkVerticalBand.overlap(levelA, levelB);
        if (band.isEmpty()) return new Result(0, List.of());
        var deltaAtoB = new BlockPos(
                (regionB.minChunkX() - regionA.minChunkX()) << 4, 0,
                (regionB.minChunkZ() - regionA.minChunkZ()) << 4);
        return run(levelA, chunkA, levelB, chunkB, deltaAtoB, band, movePlayers,
                ChunkRegionSwap::exchangeSectionBytes);
    }

    // ---------------------------------------------------------------- shared pipeline

    /**
     * The order matters and is the same for both paths.
     *
     * <p>Payloads are captured before anything moves; block entities are detached before the payload
     * exchange so no stale ticker survives; the payload exchange happens next; block entities are then
     * rebuilt at the coordinates their blocks occupy; derived state is recomputed; and finally entities
     * and players are relocated onto ground that already exists.
     */
    private static Result run(ServerLevel levelA, LevelChunk chunkA, ServerLevel levelB, LevelChunk chunkB,
                              BlockPos deltaAtoB, ChunkVerticalBand band, boolean movePlayers,
                              SectionExchange exchange) {
        var deltaBtoA = deltaAtoB.multiply(-1);
        var sameLevel = levelA == levelB;

        // 1. Capture block entities that will actually move (those inside the exchanged band).
        var blockEntitiesA = snapshotBlockEntities(chunkA, levelA, band);
        var blockEntitiesB = snapshotBlockEntities(chunkB, levelB, band);

        // 2. Capture entities and players, limited to the band so untouched sections keep their mobs.
        var entitiesA = new ArrayList<Entity>();
        var entitiesB = new ArrayList<Entity>();
        var playersA = new ArrayList<ServerPlayer>();
        var playersB = new ArrayList<ServerPlayer>();
        collectEntities(levelA, chunkA.getPos(), band, entitiesA, playersA, movePlayers);
        collectEntities(levelB, chunkB.getPos(), band, entitiesB, playersB, movePlayers);

        // 3. Detach block entities on both sides (drops tickers and game-event listeners).
        chunkA.clearAllBlockEntities();
        if (!sameLevel || chunkA != chunkB) chunkB.clearAllBlockEntities();

        // 4. Move the block and biome payload.
        exchange.exchange(levelA, chunkA, levelB, chunkB, band);

        // 5. Rebuild block entities where their blocks now sit.
        restoreBlockEntities(chunkB, levelB, blockEntitiesA, deltaAtoB, band);
        restoreBlockEntities(chunkA, levelA, blockEntitiesB, deltaBtoA, band);

        // 6. Refresh lighting immediately, and refresh the client view. Lighting is the one piece that
        //    cannot be deferred: discarding a chunk's light data is what triggers a rebuild, and a chunk
        //    left discarded while waiting for a budgeted queue renders pitch black. Heightmap priming — the
        //    genuinely expensive part — is deferred by the caller instead, since it feeds spawn and map
        //    logic rather than anything the player sees.
        refreshLighting(levelA, chunkA);
        if (!sameLevel || chunkA != chunkB) {
            refreshLighting(sameLevel ? levelA : levelB, chunkB);
        }
        resend(levelA, chunkA);
        if (!sameLevel || chunkA != chunkB) {
            resend(sameLevel ? levelA : levelB, chunkB);
        }

        // 7. Move entities, then players, so riders land on terrain that already exists.
        var moved = 0;
        for (var entity : entitiesA) {
            if (teleportRelative(levelB, entity, deltaAtoB)) moved++;
        }
        for (var entity : entitiesB) {
            if (teleportRelative(levelA, entity, deltaBtoA)) moved++;
        }
        var movedPlayers = new ArrayList<ServerPlayer>();
        for (var player : playersA) {
            if (movePlayer(levelB, player, deltaAtoB)) movedPlayers.add(player);
        }
        for (var player : playersB) {
            if (movePlayer(levelA, player, deltaBtoA)) movedPlayers.add(player);
        }

        // 8. Mark dirty immediately so a crash cannot lose the swap; the deferred refresh re-saves too.
        chunkA.markUnsaved();
        if (!sameLevel || chunkA != chunkB) chunkB.markUnsaved();
        return new Result(moved, List.copyOf(movedPlayers));
    }

    /** How section payloads move between the two chunks. */
    @FunctionalInterface
    private interface SectionExchange {
        void exchange(ServerLevel levelA, LevelChunk chunkA, ServerLevel levelB, LevelChunk chunkB,
                      ChunkVerticalBand band);
    }

    // ---------------------------------------------------------------- section payload

    /** Same-level fast path: exchange whole section objects between the two arrays. */
    private static void swapSectionArrays(ServerLevel levelA, LevelChunk chunkA, ServerLevel levelB,
                                          LevelChunk chunkB, ChunkVerticalBand band) {
        LevelChunkSection[] sectionsA = chunkA.getSections();
        LevelChunkSection[] sectionsB = chunkB.getSections();
        var count = Math.min(sectionsA.length, sectionsB.length);
        for (var i = 0; i < count; i++) {
            var swap = sectionsA[i];
            sectionsA[i] = sectionsB[i];
            sectionsB[i] = swap;
        }
    }

    /**
     * Cross-level path: exchange section content through a byte round-trip over the shared band.
     *
     * <p>Section objects cannot be shared between levels (their palette factories differ), so the
     * payload is serialised and read back in place. {@code recalcBlockCounts()} is required afterwards:
     * the wire format carries only the block and fluid counts, so the random-tick counters would
     * otherwise keep the previous section's values.
     */
    private static void exchangeSectionBytes(ServerLevel levelA, LevelChunk chunkA, ServerLevel levelB,
                                             LevelChunk chunkB, ChunkVerticalBand band) {
        var bufferA = new FriendlyByteBuf(Unpooled.buffer(8192));
        var bufferB = new FriendlyByteBuf(Unpooled.buffer(8192));
        try {
            for (var sectionY = band.minSectionY(); sectionY <= band.maxSectionY(); sectionY++) {
                var indexA = ChunkVerticalBand.sectionIndex(levelA, sectionY);
                var indexB = ChunkVerticalBand.sectionIndex(levelB, sectionY);
                var sectionsA = chunkA.getSections();
                var sectionsB = chunkB.getSections();
                if (indexA < 0 || indexA >= sectionsA.length) continue;
                if (indexB < 0 || indexB >= sectionsB.length) continue;
                var sectionA = sectionsA[indexA];
                var sectionB = sectionsB[indexB];

                bufferA.clear();
                sectionA.write(bufferA);
                bufferB.clear();
                sectionB.write(bufferB);

                bufferB.readerIndex(0);
                sectionA.read(bufferB);
                bufferA.readerIndex(0);
                sectionB.read(bufferA);

                sectionA.recalcBlockCounts();
                sectionB.recalcBlockCounts();
            }
        } finally {
            bufferA.release();
            bufferB.release();
        }
    }

    // ---------------------------------------------------------------- block entities

    /**
     * Captures block entities whose position is inside {@code band}, keyed by absolute position.
     *
     * <p>{@code saveWithFullMetadata} also records x/y/z, but {@link BlockEntity#loadStatic} takes the
     * position as an argument, so restoring at relocated coordinates is what actually moves them.
     */
    private static Map<BlockPos, CompoundTag> snapshotBlockEntities(LevelChunk chunk, ServerLevel level,
                                                                    ChunkVerticalBand band) {
        var result = new HashMap<BlockPos, CompoundTag>();
        var registryAccess = level.registryAccess();
        for (var entry : chunk.getBlockEntities().entrySet()) {
            if (!band.contains(entry.getKey().getY())) continue;
            result.put(entry.getKey().immutable(), entry.getValue().saveWithFullMetadata(registryAccess));
        }
        return result;
    }

    /**
     * Rebuilds captured block entities inside {@code chunk}, shifted by {@code delta}.
     *
     * <p>The shift is essential: after the payload exchange this chunk holds the other region's blocks,
     * so a block entity must be recreated where its block now sits, not where it came from.
     */
    private static void restoreBlockEntities(LevelChunk chunk, ServerLevel level,
                                             Map<BlockPos, CompoundTag> tags, BlockPos delta,
                                             ChunkVerticalBand band) {
        var registryAccess = level.registryAccess();
        for (var entry : tags.entrySet()) {
            var pos = entry.getKey().offset(delta.getX(), delta.getY(), delta.getZ());
            if (!band.contains(pos.getY())) continue;
            if (!ChunkPos.containing(pos).equals(chunk.getPos())) continue;
            var state = level.getBlockState(pos);
            if (!state.hasBlockEntity()) continue;
            var blockEntity = BlockEntity.loadStatic(pos, state, entry.getValue(), registryAccess);
            if (blockEntity == null) continue;
            chunk.addAndRegisterBlockEntity(blockEntity);
        }
    }

    // ---------------------------------------------------------------- derived state

    /**
     * Recomputes lighting for one chunk whose contents just moved.
     *
     * <p>Lighting cannot be deferred. {@code setLightEnabled(pos, false)} discards the light data of the
     * chunk immediately, so a chunk discarded now but rebuilt later renders pitch black in the meantime —
     * which is exactly why a large swap left both regions dark while the budgeted queue worked through
     * them. This half is cheap: bookkeeping plus a rebuild request the light engine amortises on its own.
     *
     * <p>Also refreshes the sky-light column heights, which the moved blocks invalidate.
     */
    static void refreshLighting(ServerLevel level, LevelChunk chunk) {
        chunk.initializeLightSources();
        chunk.setLightCorrect(false);
        var lightEngine = level.getChunkSource().getLightEngine();
        var pos = chunk.getPos();
        lightEngine.retainData(pos, true);
        // Dropping and re-enabling light discards the stale propagation and requests a rebuild, which
        // vanilla then amortises over subsequent ticks instead of stalling this one.
        lightEngine.setLightEnabled(pos, false);
        lightEngine.setLightEnabled(pos, true);
        lightEngine.propagateLightSources(pos);
    }

    /**
     * Primes heightmaps and re-sends the chunk; this is the half that is safe to defer.
     *
     * <p>Priming walks every column from the top of the world down to the surface — tens of thousands of
     * block reads per chunk — which is why it is budgeted. Heightmaps feed spawn and map logic but not
     * lighting, so a short delay is invisible where the cost would not be. Priming is limited to the types
     * the chunk already tracks: priming all seven would create heightmaps this chunk never had and roughly
     * double the column scan for no benefit.
     *
     * <p>The chunk is re-sent because heightmap data is part of the chunk packet, and because a client may
     * have re-entered the area since the swap; re-sending is idempotent, so the earlier send at swap time
     * being repeated here costs one packet per chunk once, not per tick.
     */
    static void refreshHeightmapsAndResend(ServerLevel level, ChunkPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
        if (chunk == null) return;
        Heightmap.primeHeightmaps(chunk, existingHeightmapTypes(chunk));
        resend(level, chunk);
        chunk.markUnsaved();
    }

    /**
     * The heightmap types {@code chunk} already tracks.
     *
     * <p>These are the ones worldgen and gameplay primed for it; re-priming exactly those keeps the
     * column scan to one pass over the types that are actually queried.
     */
    private static EnumSet<Heightmap.Types> existingHeightmapTypes(LevelChunk chunk) {
        var types = EnumSet.noneOf(Heightmap.Types.class);
        for (var entry : chunk.getHeightmaps()) {
            types.add(entry.getKey());
        }
        if (types.isEmpty()) types.add(Heightmap.Types.WORLD_SURFACE);
        return types;
    }

    // ---------------------------------------------------------------- entities

    /**
     * Pure decision: which offset moves an entity standing in {@code entityChunk} to its paired cell.
     *
     * <p>Extracted so the mapping can be unit-tested without a live world. Returns {@link BlockPos#ZERO}
     * when the entity is in neither chunk, so callers skip it instead of relocating it wrongly.
     */
    static BlockPos deltaFor(ChunkPos entityChunk, ChunkPos chunkA, ChunkPos chunkB,
                             BlockPos deltaAtoB, BlockPos deltaBtoA) {
        if (entityChunk.equals(chunkA)) return deltaAtoB;
        if (entityChunk.equals(chunkB)) return deltaBtoA;
        return BlockPos.ZERO;
    }

    /** Collects entities in {@code chunkPos} that stand inside {@code band}. */
    private static void collectEntities(ServerLevel level, ChunkPos chunkPos, ChunkVerticalBand band,
                                        List<Entity> entities, List<ServerPlayer> players,
                                        boolean movePlayers) {
        var aabb = new AABB(chunkPos.getMinBlockX(), band.minBlockY(), chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX() + 1.0, band.maxBlockY() + 1.0, chunkPos.getMaxBlockZ() + 1.0);
        for (var entity : level.getEntitiesOfClass(Entity.class, aabb, candidate -> !candidate.isRemoved())) {
            // Passengers travel with their root vehicle; moving one alone would orphan the rest.
            if (entity.getVehicle() != null) continue;
            if (entity instanceof ServerPlayer player) {
                if (movePlayers && !player.isSpectator()) players.add(player);
            } else {
                entities.add(entity);
            }
        }
    }

    private static boolean teleportRelative(ServerLevel destinationLevel, Entity entity, BlockPos delta) {
        if (delta.equals(BlockPos.ZERO)) return false;
        if (EntityMotionGuard.shouldBlockTeleport(entity)) return false;
        var target = clampY(entity.position().add(delta.getX(), delta.getY(), delta.getZ()), destinationLevel);
        return TeleportSync.teleportInstantly(entity, destinationLevel, target);
    }

    private static boolean movePlayer(ServerLevel destinationLevel, ServerPlayer player, BlockPos delta) {
        if (delta.equals(BlockPos.ZERO)) return false;
        var target = clampY(player.position().add(delta.getX(), delta.getY(), delta.getZ()), destinationLevel);
        var safe = TeleportSafety.findSafe(player, destinationLevel, target);
        var destination = safe != null ? safe : target;
        if (!TeleportSync.teleportInstantly(player, destinationLevel, destination,
                player.getYRot(), player.getXRot())) {
            return false;
        }
        player.resetFallDistance();
        return true;
    }

    /** Keeps a relocated position inside the destination level's vertical bounds. */
    private static net.minecraft.world.phys.Vec3 clampY(net.minecraft.world.phys.Vec3 position,
                                                         ServerLevel level) {
        var minY = level.getMinY();
        var maxY = level.getMaxY() - 1.0;
        if (position.y >= minY && position.y <= maxY) return position;
        return new net.minecraft.world.phys.Vec3(position.x, Math.clamp(position.y, minY, maxY), position.z);
    }

    // ---------------------------------------------------------------- resend

    /** Sends the whole chunk (blocks + light) as one packet per tracking player. */
    static void resend(ServerLevel level, LevelChunk chunk) {
        var lightEngine = level.getLightEngine();
        var packet = new ClientboundLevelChunkWithLightPacket(chunk, lightEngine, null, null);
        for (var player : level.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false)) {
            player.connection.send(packet);
        }
    }

}
