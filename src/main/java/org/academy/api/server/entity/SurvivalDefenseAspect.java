package org.academy.api.server.entity;

/**
 * Independently composable parts of an entity's authoritative survival state.
 */
public enum SurvivalDefenseAspect {
    /** Prevents health from being forced below the profile's configured floor. */
    HEALTH_FLOOR,
    /** Rejects and repairs injected dead flags, death timers, and dying poses. */
    DEATH_STATE,
    /** Rejects non-lifecycle removal reasons and repairs a corrupted removal marker. */
    REMOVAL,
    /** Restores a protected entity to its authoritative server-level lookup. */
    LEVEL_MEMBERSHIP
}
