package org.academy.api.server.ability.electromaster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.electromaster.MagneticMovement;

/** Collision-preserving flight solver. Inputs can come from player controls, AI or a program. */
public final class MagneticLevitation {
    private final MagneticSupportQuery support = new MagneticSupportQuery();

    public boolean supported(LivingEntity subject, double radius) {
        return subject.level() instanceof ServerLevel level && support.supported(level, subject.getBoundingBox(), radius);
    }

    public Vec3 velocity(LivingEntity subject, double forward, double strafe, double vertical, double speed, double radius) {
        if (!(subject.level() instanceof ServerLevel level) || !supported(subject, radius)
                || !Double.isFinite(vertical)) return Vec3.ZERO;
        var horizontal = MagneticMovement.flightDirection(subject.getYRot(), forward, strafe, 1);
        var direction = horizontal.add(0, Math.clamp(vertical, -1, 1), 0);
        if (direction.lengthSqr() > 1) direction = direction.normalize();
        var desired = MagneticMovement.approach(subject.getDeltaMovement(), direction.scale(speed));
        var bounds = subject.getBoundingBox();
        if (support.supported(level, bounds.move(desired), radius)) return desired;
        // Slide along the boundary by accepting each supported axis separately.
        var accepted = Vec3.ZERO;
        for (var axis : new Vec3[]{new Vec3(desired.x, 0, 0), new Vec3(0, desired.y, 0), new Vec3(0, 0, desired.z)}) {
            if (support.supported(level, bounds.move(accepted.add(axis)), radius)) accepted = accepted.add(axis);
        }
        return accepted;
    }
}
