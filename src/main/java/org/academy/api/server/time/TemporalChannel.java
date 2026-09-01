package org.academy.api.server.time;

/**
 * Independently controllable parts of world simulation.
 *
 * <p>The initial implementation wires entity freeze immunity. The remaining
 * channels define the stable boundary used by the later level and scheduled
 * tick integrations.</p>
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
    CLIENT_VISUAL
}
