package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;

/**
 * Server-thread access to one structure's fixed local grid. No entity, player, raw level,
 * or backend storage address is required. An access object remains bound to its original
 * runtime handle and is never reactivated after that handle has been unloaded.
 */
public interface StructureWorldAccess {
    StructureRef reference();

    /**
     * Reads the live block state and detached block-entity data. Unavailable content must
     * not be reported as air; callers must handle the backend's loaded-content boundary.
     *
     * @throws IllegalArgumentException if the local position is outside the backend's bounds
     * @throws IllegalStateException if called off the server thread, the handle is not active,
     *                               or the requested content is unavailable
     */
    StructureBlockData read(BlockPos localPosition);

    /**
     * Changes one cell only if both its current state and complete saved block-entity data
     * equal {@code expected}. The backend owns validation and block update handling; callers
     * cannot select raw world-write flags. This is a server operation, not a player action:
     * callers remain responsible for permission checks and consuming or returning items.
     *
     * <p>Callback side effects and writes to other cells are not a multi-cell transaction.
     * In particular, {@link StructureWriteResult#APPLIED} does not promise that the replacement
     * remains unchanged after normal block callbacks or later ticks.</p>
     *
     * @throws IllegalArgumentException if either block-entity image exceeds the backend's write budget
     * @throws IllegalStateException if called off the server thread
     */
    StructureWriteResult compareAndSet(
            BlockPos localPosition,
            StructureBlockData expected,
            StructureBlockData replacement);
}
