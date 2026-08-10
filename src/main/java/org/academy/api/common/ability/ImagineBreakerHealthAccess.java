package org.academy.api.common.ability;

/**
 * Public compatibility hook for damage that is allowed to pass through vector defenses.
 */
public interface ImagineBreakerHealthAccess {
    /**
     * @param amount health points to remove
     */
    void imaginebreaker(float amount);
}
