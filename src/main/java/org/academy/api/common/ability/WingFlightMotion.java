package org.academy.api.common.ability;

import net.minecraft.world.phys.Vec3;

/** Shared, side-independent wing movement. Time advances with local physics, never packet count. */
public final class WingFlightMotion {
    private WingFlightMotion() {
    }

    public static float clampMomentum(float value) {
        return Float.isFinite(value) ? Math.clamp(value, 0, 1) : 1;
    }

    /** Original airborne drag and gravity while steering; hover is gravity-free. */
    public static Vec3 afterMove(Vec3 velocity, int buttons) {
        return (buttons == 0 ? velocity : velocity.add(0, -0.08, 0)).multiply(0.91, 0.98, 0.91);
    }

    public static Vec3 step(Vec3 velocity, WingControlIntent input, int previousButtons,
                            float momentum, double movementScale) {
        var look = Vec3.directionFromRotation(input.pitch(), input.yaw());
        if (input.buttons() == 0) {
            var retained = clampMomentum(momentum);
            if (retained == 0) return Vec3.ZERO;
            if (previousButtons != 0) velocity = velocity.scale(retained);
            var result = velocity.multiply(0.995, Math.abs(velocity.y) > 0.25 ? 0.685 : 0, 0.995);
            return result.lengthSqr() < 1.0E-6 ? Vec3.ZERO : result;
        }
        if (input.has(WingControlIntent.BOOST)) return velocity.add(look.scale(2 * movementScale));
        if (input.has(WingControlIntent.FRONT)) {
            var push = look.add(0, 0.35, 0).scale(0.2 * movementScale);
            velocity = velocity.add(push.x, push.y * 1.5, push.z);
        }
        if (input.has(WingControlIntent.BACK)) velocity = velocity.add(look.add(0, -0.35, 0).scale(-0.2 * movementScale));
        if (input.has(WingControlIntent.LEFT)) velocity = velocity.add(new Vec3(look.z, -look.y + 0.15, -look.x).scale(0.2 * movementScale));
        if (input.has(WingControlIntent.RIGHT)) velocity = velocity.add(new Vec3(-look.z, -look.y + 0.15, look.x).scale(0.2 * movementScale));
        return velocity;
    }
}
