package org.academy.api.server.ability.vector;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.vector.EffectOperation;

import java.util.Objects;
import java.util.UUID;

/** Existing-entity operation. Ownership transfers to the actor; original payload and collision code remain. */
public record VectorOperationRequest(UUID requestId, EffectOperation operation, Vec3 velocity,
                                     double maximumRange, int durationTicks) {
    public static final double MAXIMUM_RANGE = 128;
    public static final double MAXIMUM_SPEED = 128;
    public static final int MAXIMUM_DURATION = 1200;

    public VectorOperationRequest {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(operation);
        Objects.requireNonNull(velocity);
        if (operation == EffectOperation.OBSERVE || !Double.isFinite(velocity.lengthSqr())
                || operation == EffectOperation.REDIRECT_EXISTING && velocity.lengthSqr() <= 1.0E-8
                || velocity.lengthSqr() > MAXIMUM_SPEED * MAXIMUM_SPEED
                || !Double.isFinite(maximumRange) || maximumRange <= 0 || maximumRange > MAXIMUM_RANGE
                || durationTicks < 1 || durationTicks > MAXIMUM_DURATION
                || operation == EffectOperation.REDIRECT_EXISTING && durationTicks != 1) {
            throw new IllegalArgumentException("Invalid vector operation bounds");
        }
    }

    public static VectorOperationRequest redirect(Vec3 velocity, double range) {
        return new VectorOperationRequest(UUID.randomUUID(), EffectOperation.REDIRECT_EXISTING, velocity, range, 1);
    }

    public static VectorOperationRequest control(Vec3 velocity, double range, int ticks) {
        return new VectorOperationRequest(UUID.randomUUID(), EffectOperation.CONTROL_EXISTING, velocity, range, ticks);
    }
}
