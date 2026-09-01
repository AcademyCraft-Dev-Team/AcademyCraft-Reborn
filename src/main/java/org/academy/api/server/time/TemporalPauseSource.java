package org.academy.api.server.time;

/** Sources of a hard time pause that may be bypassed independently. */
public enum TemporalPauseSource {
    VANILLA_FREEZE,
    ACADEMY_PAUSE,
    EXTERNAL_COMPATIBILITY
}
