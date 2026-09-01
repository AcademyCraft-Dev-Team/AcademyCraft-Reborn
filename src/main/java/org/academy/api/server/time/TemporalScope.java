package org.academy.api.server.time;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Immutable spatial boundary of a temporal field.
 *
 * <p>A save scope affects every loaded dimension. A dimension scope affects
 * one dimension. A sphere scope additionally requires a position inside its
 * radius.</p>
 */
public sealed interface TemporalScope permits TemporalScope.Save,
        TemporalScope.Dimension, TemporalScope.Sphere {
    static Save save() {
        return Save.INSTANCE;
    }

    static Dimension dimension(ResourceKey<Level> dimension) {
        return new Dimension(dimension);
    }

    static Sphere sphere(
            ResourceKey<Level> dimension,
            Vec3 center,
            double radius
    ) {
        return new Sphere(dimension, center, radius);
    }

    /**
     * Tests this scope against a dimension and optional position.
     * Spatial scopes never match when {@code position} is {@code null}.
     */
    boolean contains(ResourceKey<Level> dimension, Vec3 position);

    /** True when this scope requires a world position to match. */
    boolean isSpatial();

    record Save() implements TemporalScope {
        private static final Save INSTANCE = new Save();

        @Override
        public boolean contains(ResourceKey<Level> dimension, Vec3 position) {
            return dimension != null;
        }

        @Override
        public boolean isSpatial() {
            return false;
        }
    }

    record Dimension(ResourceKey<Level> dimension) implements TemporalScope {
        public Dimension {
            Objects.requireNonNull(dimension, "dimension");
        }

        @Override
        public boolean contains(
                ResourceKey<Level> candidateDimension,
                Vec3 position
        ) {
            return dimension.equals(candidateDimension);
        }

        @Override
        public boolean isSpatial() {
            return false;
        }
    }

    record Sphere(
            ResourceKey<Level> dimension,
            Vec3 center,
            double radius
    ) implements TemporalScope {
        public Sphere {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(center, "center");
            if (!Double.isFinite(center.x)
                    || !Double.isFinite(center.y)
                    || !Double.isFinite(center.z)) {
                throw new IllegalArgumentException("Sphere center must be finite.");
            }
            if (!Double.isFinite(radius) || radius <= 0.0D) {
                throw new IllegalArgumentException("Sphere radius must be finite and positive.");
            }
        }

        @Override
        public boolean contains(
                ResourceKey<Level> candidateDimension,
                Vec3 position
        ) {
            return position != null
                    && dimension.equals(candidateDimension)
                    && center.distanceToSqr(position) <= radius * radius;
        }

        @Override
        public boolean isSpatial() {
            return true;
        }
    }
}
