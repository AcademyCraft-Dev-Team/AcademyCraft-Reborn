package org.academy.internal.common.structure.upgrade.damage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.structure.StructureHandle;

/** An already loaded activation. Storage addresses never escape the damage implementation. */
public interface DamageDomain {
    StructureHandle handle();
    ServerLevel storage();
    BlockPos origin();
    AABB localBounds();
    boolean current();
    /** Read-only proof for a just-emptied activation still owning its plot until tick cleanup. */
    default boolean retiringEmptyCell(BlockPos local, org.academy.api.common.structure.StructurePose pose) { return false; }
    long contentRevision();
    /** Invalidate observations immediately, even if expensive rebuilding waits for the batch. */
    void changed();
    void flush();

    default boolean transactionPending() {
        var host = storage().getServer();
        return org.academy.internal.common.structure.transaction.StructureCaptureCoordinator.isPending(host, handle().reference())
                || org.academy.internal.common.structure.upgrade.lifecycle.MarkedGridRestoration.pending(host, handle().reference());
    }

    default net.minecraft.world.level.chunk.@org.jspecify.annotations.Nullable LevelChunk chunk(BlockPos position) {
        return storage().getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
    }

    default BlockPos position(BlockPos local) { return origin().offset(local); }
}
