package org.academy.api.common.ability;

/**
 * Public compatibility hook for damage that is allowed to pass through vector defenses.
 */
public interface ImagineBreakerHealthAccess {
    /**
     * Removes health without vector-defense reduction. A lethal removal must leave vector
     * protection and continue through the normal death pipeline.
     *
     * @param amount health points to remove
     */
    void imaginebreaker(float amount);
}
