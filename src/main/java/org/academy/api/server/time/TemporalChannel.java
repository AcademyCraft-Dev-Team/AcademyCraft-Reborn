package org.academy.api.server.time;

import java.util.EnumSet;
import java.util.Set;

/**
 * Independently controllable parts of world simulation.
 *
 * <p>Each dispatcher reads only its own channel, allowing skills to control
 * entity, block-entity and scheduled simulation independently while sharing
 * the same field and scope model.</p>
 */
public enum TemporalChannel {
    SERVER_CLOCK,
    LEVEL_CLOCK,
    WEATHER_AND_RAID,
    ENTITY,
    BLOCK_ENTITY,
    SCHEDULED_BLOCK,
    SCHEDULED_FLUID,
    RANDOM_TICK,
    BLOCK_EVENT,
    ACADEMY_SCHEDULER,
    CLIENT_VISUAL;

    private static final Set<TemporalChannel> WORLD_SIMULATION = Set.copyOf(
            EnumSet.of(
                    LEVEL_CLOCK,
                    WEATHER_AND_RAID,
                    ENTITY,
                    BLOCK_ENTITY,
                    SCHEDULED_BLOCK,
                    SCHEDULED_FLUID,
                    RANDOM_TICK,
                    BLOCK_EVENT,
                    ACADEMY_SCHEDULER
            )
    );

    /**
     * All authoritative world-simulation channels. The independent server
     * heartbeat and client-only presentation clock are intentionally excluded.
     */
    public static Set<TemporalChannel> worldSimulation() {
        return WORLD_SIMULATION;
    }
}
