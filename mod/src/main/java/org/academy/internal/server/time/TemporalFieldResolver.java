package org.academy.internal.server.time;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.academy.api.server.time.*;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Pure field-composition rule shared by runtime dispatchers.
 */
final class TemporalFieldResolver {
    private TemporalFieldResolver() {
    }

    static double resolve(
            Iterable<TemporalField> fields,
            ResourceKey<Level> dimension,
            @Nullable Vec3 position,
            TemporalChannel channel,
            Predicate<TemporalPauseSource> pauseImmunity
    ) {
        return resolve(
                fields,
                dimension,
                position,
                null,
                channel,
                pauseImmunity
        );
    }

    static double resolve(
            Iterable<TemporalField> fields,
            ResourceKey<Level> dimension,
            @Nullable Vec3 position,
            @Nullable UUID entityId,
            TemporalChannel channel,
            Predicate<TemporalPauseSource> pauseImmunity
    ) {
        if (channel == null) {
            throw new IllegalArgumentException("Temporal channel cannot be null.");
        }
        var scales = new ArrayList<Double>();
        for (var field : fields) {
            if (!field.channels().contains(channel)
                    || !field.scope().contains(dimension, position, entityId)) {
                continue;
            }
            if (field.scale() == 0.0D
                    && pauseImmunity.test(field.pauseSource())) {
                continue;
            }
            scales.add(field.scale());
        }
        return TemporalScale.compose(
                scales,
                false,
                TemporalScale.DEFAULT_MAX_SCALE
        );
    }

    /**
     * Resolves only save-scoped contributions for a server-global clock.
     */
    static double resolveSave(
            Iterable<TemporalField> fields,
            TemporalChannel channel
    ) {
        if (channel == null) {
            throw new IllegalArgumentException("Temporal channel cannot be null.");
        }
        var scales = new ArrayList<Double>();
        for (var field : fields) {
            if (field.scope() instanceof TemporalScope.Save
                    && field.channels().contains(channel)) {
                scales.add(field.scale());
            }
        }
        return TemporalScale.compose(
                scales,
                false,
                TemporalScale.DEFAULT_MAX_SCALE
        );
    }
}
