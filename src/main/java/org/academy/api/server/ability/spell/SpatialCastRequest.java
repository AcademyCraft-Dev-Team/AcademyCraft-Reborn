package org.academy.api.server.ability.spell;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Optional spatial inputs in the caster's dimension. Null inputs preserve native behavior.
 * An endpoint is an aiming/raycast limit, not a promise that a projectile will stop there.
 * A target is an identity constraint; it never bypasses the provider's range or hit admission.
 * No field grants casting authority or changes the actual caster's position/orientation.
 */
public record SpatialCastRequest(@Nullable Vec3 origin, @Nullable Vec3 endpoint,
                                 @Nullable Vec3 direction, @Nullable LivingEntity target) {
    public static final int ORIGIN = 1;
    public static final int ENDPOINT = 2;
    public static final int DIRECTION = 4;
    public static final int TARGET = 8;
    public static final int ALL = ORIGIN | ENDPOINT | DIRECTION | TARGET;

    public static SpatialCastRequest nativeCast() { return new SpatialCastRequest(null, null, null, null); }

    public boolean isNative() { return inputMask() == 0; }

    public int inputMask() {
        return (origin == null ? 0 : ORIGIN) | (endpoint == null ? 0 : ENDPOINT)
                | (direction == null ? 0 : DIRECTION) | (target == null ? 0 : TARGET);
    }
}
