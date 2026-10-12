package org.academy.api.common.structure;

import java.util.UUID;

/**
 * Entity-independent runtime view of a structure. Read and mutation methods run on the
 * owning server thread. A reference survives normal saves and reloads; an unloaded handle
 * and its world access remain permanently invalid even if that reference is loaded again.
 */
public interface StructureHandle {
    /** The dimension identifies the visible host world, never a storage container. */
    StructureRef reference();

    StructureLifecycle lifecycle();

    /** A fresh identity for each activation, including reloads of the same persistent reference. */
    UUID runtimeId();

    /** Monotonic within this activation; changes to pose or local block states invalidate old hits. */
    long geometryRevision();

    /** @throws IllegalStateException if this handle is not active or the thread is wrong */
    StructurePose pose();

    /**
     * Changes the local-to-host transform without moving or renumbering local cells.
     * This does not provide collision checking, passenger transport, or physical motion.
     *
     * @throws IllegalStateException if this handle is not active or the thread is wrong
     */
    void setPose(StructurePose pose);

    /** Returns access bound to this exact runtime handle, including after it is invalidated. */
    StructureWorldAccess world();
}
