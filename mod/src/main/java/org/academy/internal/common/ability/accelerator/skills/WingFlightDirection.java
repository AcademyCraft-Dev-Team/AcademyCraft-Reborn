package org.academy.internal.common.ability.accelerator.skills;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class WingFlightDirection {
    private WingFlightDirection() {
    }

    public static Vec3 resolve(Vec3 fallback, float yRot, float xRot) {
        if (!Float.isFinite(yRot) || !Float.isFinite(xRot)) return fallback;
        return Vec3.directionFromRotation(Mth.clamp(xRot, -90.0f, 90.0f), Mth.wrapDegrees(yRot));
    }
}
