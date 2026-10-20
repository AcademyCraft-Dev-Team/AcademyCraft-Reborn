package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.academy.AcademyCraft;
import org.academy.internal.common.structure.space.NativeStructureStorageErrors;
import org.academy.mixin.common.ChunkMapAccessor;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Writes only the chunks a capture touched and waits for their region writes. The full-level saves
 * in the capture path are dominated by unrelated plots and host chunks; this keeps the journal's
 * durability order while scoping the disk work to the blocks that actually changed.
 */
public final class TargetedChunkDurability {
    private TargetedChunkDurability() {
    }

    public static boolean saveAround(ServerLevel level, Collection<BlockPos> positions) {
        var chunks = new LinkedHashSet<ChunkPos>();
        for (var position : positions) {
            chunks.add(new ChunkPos(position.getX() >> 4, position.getZ() >> 4));
        }
        return save(level, chunks);
    }

    public static boolean save(ServerLevel level, Collection<ChunkPos> chunks) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunks, "chunks");
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Targeted chunk durability requires the server thread");
        }
        if (chunks.isEmpty()) return true;
        if (!(level.getChunkSource() instanceof ServerChunkCache cache)) return false;
        try {
            for (var pos : chunks) {
                LevelChunk chunk = level.getChunk(pos.x(), pos.z());
                ((ChunkMapAccessor) (Object) cache.chunkMap).academy$save(chunk);
            }
            // ChunkMap.save queues region writes; join the same synchronize barrier used by
            // ServerChunkCache.save(true) before the journal retires the transaction.
            cache.chunkMap.synchronize(true).join();
            return !NativeStructureStorageErrors.failed(level);
        } catch (RuntimeException failure) {
            AcademyCraft.LOGGER.error("Could not durably save capture chunks {}", chunks, failure);
            return false;
        }
    }
}
