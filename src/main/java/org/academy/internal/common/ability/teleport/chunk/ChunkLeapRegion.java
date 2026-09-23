package org.academy.internal.common.ability.teleport.chunk;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * An axis-aligned rectangle of chunks in a single dimension, plus the codec used to move it
 * between the client map screen and the server.
 */
public record ChunkLeapRegion(ResourceKey<Level> dimension, int minChunkX, int minChunkZ, int width, int height) {
    public static final int MAX_SIDE = 64;
    public static final int MAX_CHUNKS = MAX_SIDE * MAX_SIDE;

    public static final StreamCodec<ByteBuf, ChunkLeapRegion> STREAM_CODEC = StreamCodec.of(
            (buf, region) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, region.dimension.identifier().toString());
                ByteBufCodecs.VAR_INT.encode(buf, region.minChunkX);
                ByteBufCodecs.VAR_INT.encode(buf, region.minChunkZ);
                ByteBufCodecs.VAR_INT.encode(buf, region.width);
                ByteBufCodecs.VAR_INT.encode(buf, region.height);
            },
            buf -> {
                var id = Identifier.tryParse(ByteBufCodecs.STRING_UTF8.decode(buf));
                var dimension = id == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, id);
                return new ChunkLeapRegion(
                        dimension,
                        clamp(ByteBufCodecs.VAR_INT.decode(buf), -MAX_CHUNKS, MAX_CHUNKS),
                        clamp(ByteBufCodecs.VAR_INT.decode(buf), -MAX_CHUNKS, MAX_CHUNKS),
                        clamp(ByteBufCodecs.VAR_INT.decode(buf), 1, MAX_SIDE),
                        clamp(ByteBufCodecs.VAR_INT.decode(buf), 1, MAX_SIDE));
            });

    public ChunkLeapRegion {
        width = Math.max(1, Math.min(MAX_SIDE, width));
        height = Math.max(1, Math.min(MAX_SIDE, height));
    }

    public static ChunkLeapRegion of(ResourceKey<Level> dimension, ChunkPos from, ChunkPos to) {
        var minX = Math.min(from.x(), to.x());
        var minZ = Math.min(from.z(), to.z());
        return new ChunkLeapRegion(dimension, minX, minZ,
                Math.abs(to.x() - from.x()) + 1, Math.abs(to.z() - from.z()) + 1);
    }

    public static ChunkLeapRegion ofChunks(ResourceKey<Level> dimension, int minChunkX, int minChunkZ,
                                           int width, int height) {
        return new ChunkLeapRegion(dimension, minChunkX, minChunkZ, width, height);
    }

    public int maxChunkX() {
        return minChunkX + width - 1;
    }

    public int maxChunkZ() {
        return minChunkZ + height - 1;
    }

    public long chunkCount() {
        return (long) width * height;
    }

    public boolean sameShape(ChunkLeapRegion other) {
        return other != null && width == other.width && height == other.height;
    }

    /** True when both regions live in the same dimension and share at least one chunk. */
    public boolean intersects(ChunkLeapRegion other) {
        if (other == null || !other.dimension().equals(dimension)) return false;
        return minChunkX <= other.maxChunkX() && maxChunkX() >= other.minChunkX()
                && minChunkZ <= other.maxChunkZ() && maxChunkZ() >= other.minChunkZ();
    }

    /** Offset that maps a chunk/block position from this region to the matching cell of {@code target}. */
    public int offsetChunkX(ChunkLeapRegion target) {
        return target.minChunkX() - minChunkX;
    }

    public int offsetChunkZ(ChunkLeapRegion target) {
        return target.minChunkZ() - minChunkZ;
    }

    public int minBlockX() {
        return minChunkX << 4;
    }

    public int minBlockZ() {
        return minChunkZ << 4;
    }

    public int maxBlockX() {
        return (maxChunkX() << 4) + 15;
    }

    public int maxBlockZ() {
        return (maxChunkZ() << 4) + 15;
    }

    public boolean contains(ChunkPos pos) {
        return pos.x() >= minChunkX && pos.x() <= maxChunkX() && pos.z() >= minChunkZ && pos.z() <= maxChunkZ();
    }

    /**
     * Shrinks this region, keeping {@code anchor} as a corner, until it holds at most {@code cap}
     * chunks. Returns {@code this} when already within the cap.
     *
     * <p>Applied live while the player drags a selection so a runaway drag can never build a giant
     * region: whatever happens with the input, the region the client renders, validates and requests is
     * bounded, and {@code anchor} staying fixed keeps the clamp predictable under the cursor.
     */
    public ChunkLeapRegion clampArea(ChunkPos anchor, long cap) {
        if (chunkCount() <= cap || cap <= 0) return this;
        var width = this.width;
        var height = this.height;
        // Shrink the longer axis first, one chunk at a time, until the area fits.
        while ((long) width * height > cap) {
            if (width >= height && width > 1) {
                width--;
            } else if (height > 1) {
                height--;
            } else {
                break;
            }
        }
        // Keep the anchor on the same corner it already occupies.
        var keepRight = anchor.x() == maxChunkX() && width < this.width;
        var keepBottom = anchor.z() == maxChunkZ() && height < this.height;
        var newMinX = keepRight ? maxChunkX() - width + 1 : minChunkX;
        var newMinZ = keepBottom ? maxChunkZ() - height + 1 : minChunkZ;
        return new ChunkLeapRegion(dimension, newMinX, newMinZ, width, height);
    }

    public ServerLevel resolve(MinecraftServer server) {
        return server == null ? null : server.getLevel(dimension);
    }

    public BlockPos minBlockPos() {
        return new BlockPos(minBlockX(), 0, minBlockZ());
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
