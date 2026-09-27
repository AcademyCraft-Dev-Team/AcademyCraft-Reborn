package org.academy.api.server.entity;

import java.util.UUID;

/**
 * An owner-scoped contribution to an entity's survival-state defense.
 */
public interface SurvivalDefenseLease extends AutoCloseable {
    UUID entityId();

    SurvivalDefenseProfile profile();

    boolean isActive();

    /**
     * Releases only this contribution. The lease may only be closed by code from its acquiring
     * code source; reflective or foreign close attempts are rejected.
     */
    @Override
    void close();
}
