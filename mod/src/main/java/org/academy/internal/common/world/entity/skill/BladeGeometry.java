package org.academy.internal.common.world.entity.skill;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

public final class BladeGeometry {
    public static final Vec3 HALF_EXTENTS = new Vec3(0.25, 0.5, 0.05);

    private static final Quaternionf BASE_ALIGNMENT = new Quaternionf()
            .rotationX(90.0f * Mth.DEG_TO_RAD)
            .mul(new Quaternionf().rotationZ(-45.0f * Mth.DEG_TO_RAD));

    private BladeGeometry() {
    }

    public static Quaternionf rotation(float yaw, float pitch, float roll) {
        return new Quaternionf()
                .rotationY(-yaw * Mth.DEG_TO_RAD)
                .mul(new Quaternionf().rotationX(pitch * Mth.DEG_TO_RAD))
                .mul(new Quaternionf().rotationZ(roll * Mth.DEG_TO_RAD))
                .mul(BASE_ALIGNMENT);
    }

    public static Vec3[] axes(float yaw, float pitch, float roll) {
        var matrix = new Matrix3f().set(rotation(yaw, pitch, roll));
        return new Vec3[]{
                new Vec3(matrix.m00, matrix.m01, matrix.m02),
                new Vec3(matrix.m10, matrix.m11, matrix.m12),
                new Vec3(matrix.m20, matrix.m21, matrix.m22)
        };
    }

    public static float yawFromDirection(Vec3 direction) {
        return (float) (Mth.atan2(-direction.x, direction.z)) * Mth.RAD_TO_DEG;
    }

    public static float pitchFromDirection(Vec3 direction) {
        var horizontal = Mth.sqrt((float) (direction.x * direction.x + direction.z * direction.z));
        return (float) (Mth.atan2(-direction.y, horizontal)) * Mth.RAD_TO_DEG;
    }

    public static Vec3 direction(float yaw, float pitch) {
        var pitchRadians = pitch * Mth.DEG_TO_RAD;
        var yawRadians = yaw * Mth.DEG_TO_RAD;
        return new Vec3(
                Math.cos(pitchRadians) * -Math.sin(yawRadians),
                -Math.sin(pitchRadians),
                Math.cos(pitchRadians) * Math.cos(yawRadians)
        );
    }

    public static Vec3 rotateTowards(Vec3 from, Vec3 to, double blend) {
        var start = from.normalize();
        var target = to.normalize();
        var axis = start.cross(target);
        if (axis.lengthSqr() < 1.0E-8) {
            if (start.dot(target) > 0.0) return target;
            axis = start.cross(new Vec3(0.0, 1.0, 0.0));
            if (axis.lengthSqr() < 1.0E-8) axis = start.cross(new Vec3(1.0, 0.0, 0.0));
        }
        var angle = Math.acos(Mth.clamp(start.dot(target), -1.0, 1.0)) * Mth.clamp(blend, 0.0, 1.0);
        return rotate(start, axis.normalize(), angle);
    }

    private static Vec3 rotate(Vec3 vector, Vec3 axis, double radians) {
        var cosine = Math.cos(radians);
        var sine = Math.sin(radians);
        return vector.scale(cosine)
                .add(axis.cross(vector).scale(sine))
                .add(axis.scale(axis.dot(vector) * (1.0 - cosine)));
    }
}
