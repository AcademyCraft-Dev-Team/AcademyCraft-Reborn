package org.academy.api.server.time;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * One composable contribution to world simulation speed.
 *
 * <p>Applicable fields multiply. A scale of zero is a hard pause whose source
 * may be ignored by an immune entity. Positive scales remain effective even
 * for time-stop-immune entities.</p>
 */
public record TemporalField(
        TemporalScope scope,
        Set<TemporalChannel> channels,
        double scale,
        TemporalPauseSource pauseSource
) {
    public TemporalField {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(channels, "channels");
        Objects.requireNonNull(pauseSource, "pauseSource");
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("At least one temporal channel is required.");
        }
        if (!Double.isFinite(scale)
                || scale < 0.0D
                || scale > TemporalScale.DEFAULT_MAX_SCALE) {
            throw new IllegalArgumentException(
                    "Temporal field scale must be finite and between zero and "
                            + TemporalScale.DEFAULT_MAX_SCALE + "."
            );
        }
        channels = Set.copyOf(EnumSet.copyOf(channels));
    }

    public static TemporalField scale(
            TemporalScope scope,
            Set<TemporalChannel> channels,
            double scale
    ) {
        return new TemporalField(
                scope,
                channels,
                scale,
                TemporalPauseSource.ACADEMY_PAUSE
        );
    }

    public static TemporalField pause(
            TemporalScope scope,
            Set<TemporalChannel> channels,
            TemporalPauseSource source
    ) {
        return new TemporalField(scope, channels, 0.0D, source);
    }

    /** Creates a field over every world-simulation channel. */
    public static TemporalField worldScale(TemporalScope scope, double scale) {
        return scale(scope, TemporalChannel.worldSimulation(), scale);
    }

    /** Creates a hard pause over every world-simulation channel. */
    public static TemporalField worldPause(
            TemporalScope scope,
            TemporalPauseSource source
    ) {
        return pause(scope, TemporalChannel.worldSimulation(), source);
    }
}
