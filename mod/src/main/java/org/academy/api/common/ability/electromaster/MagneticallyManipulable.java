package org.academy.api.common.ability.electromaster;

/**
 * Marks an entity as a valid magnetic-manipulation target independently of its registry name,
 * tags, or equipped items.
 */
public interface MagneticallyManipulable {
    /**
     * Returns whether magnetic manipulation may currently target this entity.
     */
    default boolean canBeMagneticallyManipulated() {
        return true;
    }
}
