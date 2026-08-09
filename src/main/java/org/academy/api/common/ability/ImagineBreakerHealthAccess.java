package org.academy.api.common.ability;

/**
 * Public compatibility hook for damage that is allowed to pass through vector defenses.
 */
public interface ImagineBreakerHealthAccess {
    /**
     * Lowers both the recorded vector-defense health and the underlying vanilla health.
     * Invalid, zero, and negative amounts are ignored; this method can never heal the player.
     * When both values reach zero, vector defenses and class-pointer replacement are ended before
     * vanilla death and respawn handling continues. Invoke this on the authoritative server player;
     * the local-player implementation only mirrors client state.
     *
     * @param amount health points to remove
     */
    void imaginebreaker(float amount);
}
