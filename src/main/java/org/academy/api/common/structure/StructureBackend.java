package org.academy.api.common.structure;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Server-owned structure lifecycle service shared by skills, programs, and non-player
 * callers. Operations run on the owning server thread; invalid thread use is rejected
 * before accessing content. Implementations may have a finite number of storage slots.
 */
public interface StructureBackend {
    Identifier id();

    /**
     * Allocates a new empty structure in a visible host world. Its reference contains that
     * host dimension, not its internal storage dimension. Returns empty when capacity is
     * unavailable; this never captures or removes blocks from the host world.
     *
     * @throws IllegalArgumentException if the host is not a valid host for this backend
     * @throws IllegalStateException if called off the owning server thread
     */
    Optional<StructureHandle> createEmpty(ServerLevel hostLevel, StructurePose pose);

    /** Finds an already loaded exact reference without loading content or adding tickets. */
    Optional<StructureHandle> resolve(StructureRef reference);

    /**
     * Explicitly loads a saved exact reference, or returns its already loaded handle.
     * Unknown identities, wrong dimensions, backends, or generations return empty.
     * A successful reload preserves the reference and returns a fresh runtime handle.
     */
    Optional<StructureHandle> load(StructureRef reference);

    /**
     * Saves and deactivates a loaded structure, permanently invalidating its handle.
     * Returns false for an unknown reference or a failed save; save failure must preserve
     * the content and must not recycle its storage slot. Unloading does not delete content
     * or free an occupied persistent slot. Separate index and chunk saves do not imply
     * crash-atomic persistence.
     */
    boolean unload(StructureRef reference);
}
