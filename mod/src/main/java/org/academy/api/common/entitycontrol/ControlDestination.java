package org.academy.api.common.entitycontrol;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

/**
 * A dynamic entity or fixed, dimension-bound destination for movement control.
 */
public sealed interface ControlDestination permits ControlDestination.Entity, ControlDestination.Position {
    record Entity(UUID uuid) implements ControlDestination {
        public Entity {
            Objects.requireNonNull(uuid, "uuid");
        }
    }

    record Position(Identifier dimension, Vec3 value) implements ControlDestination {
        public Position {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(value, "value");
            if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
                throw new IllegalArgumentException("Destination position must be finite");
            }
        }
    }
}
