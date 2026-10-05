package org.academy.internal.common.world.entity.skill;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public record BladeTransform(Vec3 position, float yaw, float pitch, float roll) {
    public static BladeTransform lerp(BladeTransform from, BladeTransform to, double blend) {
        var t = Mth.clamp(blend, 0.0, 1.0);
        var position = from.position.lerp(to.position, t);
        var direction = BladeGeometry.rotateTowards(
                BladeGeometry.direction(from.yaw, from.pitch),
                BladeGeometry.direction(to.yaw, to.pitch),
                t
        );
        var roll = from.roll + Mth.wrapDegrees(to.roll - from.roll) * (float) t;
        return new BladeTransform(
                position,
                BladeGeometry.yawFromDirection(direction),
                BladeGeometry.pitchFromDirection(direction),
                roll
        );
    }
}
