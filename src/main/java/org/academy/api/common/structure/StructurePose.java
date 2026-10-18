package org.academy.api.common.structure;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;

import java.util.Objects;

/**
 * Rigid local-to-world transform with unit scale. The origin is the world position of local
 * (0, 0, 0), not the center of mass. Quaternion components are copied and normalized.
 */
public record StructurePose(
        Vec3 worldOrigin,
        double rotationX,
        double rotationY,
        double rotationZ,
        double rotationW
) {
    public StructurePose {
        worldOrigin = requireFinite(worldOrigin, "worldOrigin");
        if (!Double.isFinite(rotationX) || !Double.isFinite(rotationY)
                || !Double.isFinite(rotationZ) || !Double.isFinite(rotationW)) {
            throw new IllegalArgumentException("rotation must be finite");
        }
        var scale = Math.max(Math.max(Math.abs(rotationX), Math.abs(rotationY)),
                Math.max(Math.abs(rotationZ), Math.abs(rotationW)));
        if (scale == 0.0) throw new IllegalArgumentException("rotation cannot be zero");
        rotationX /= scale;
        rotationY /= scale;
        rotationZ /= scale;
        rotationW /= scale;
        var length = Math.sqrt(rotationX * rotationX + rotationY * rotationY
                + rotationZ * rotationZ + rotationW * rotationW);
        rotationX /= length;
        rotationY /= length;
        rotationZ /= length;
        rotationW /= length;
    }

    public StructurePose(Vec3 worldOrigin, Quaterniondc rotation) {
        this(worldOrigin, Objects.requireNonNull(rotation, "rotation").x(),
                rotation.y(), rotation.z(), rotation.w());
    }

    public static StructurePose identity(Vec3 worldOrigin) {
        return new StructurePose(worldOrigin, 0.0, 0.0, 0.0, 1.0);
    }

    /**
     * Adapts the legacy entity pose: positive yaw rotates local +X toward +Z around its
     * horizontal pivot. The entity position is the unrotated grid origin.
     */
    public static StructurePose fromYawAroundPivot(
            Vec3 entityPosition, double yawDegrees, Vec3 localPivot
    ) {
        requireFinite(entityPosition, "entityPosition");
        requireFinite(localPivot, "localPivot");
        if (!Double.isFinite(yawDegrees)) {
            throw new IllegalArgumentException("yawDegrees must be finite");
        }
        var halfAngle = Math.toRadians(Math.IEEEremainder(yawDegrees, 360.0)) * -0.5;
        var rotation = new StructurePose(
                Vec3.ZERO, 0.0, Math.sin(halfAngle), 0.0, Math.cos(halfAngle));
        var origin = entityPosition.add(localPivot)
                .subtract(rotation.localToWorldDirection(localPivot));
        return new StructurePose(origin, rotation.rotationX, rotation.rotationY,
                rotation.rotationZ, rotation.rotationW);
    }

    /** Linear origin + shortest-arc quaternion interpolation, clamped to [0, 1]. */
    public StructurePose lerp(StructurePose other, double delta) {
        Objects.requireNonNull(other, "other");
        double t = Math.max(0.0, Math.min(1.0, delta));
        var origin = worldOrigin.lerp(other.worldOrigin, t);
        var rotation = rotation().slerp(other.rotation(), t);
        return new StructurePose(origin, rotation);
    }

    /** Returns a mutable copy; changing it never changes this pose. */
    public Quaterniond rotation() {
        return new Quaterniond(rotationX, rotationY, rotationZ, rotationW);
    }

    public Vec3 localToWorldPoint(Vec3 localPoint) {
        return requireFinite(worldOrigin.add(localToWorldDirection(localPoint)), "worldPoint");
    }

    public Vec3 worldToLocalPoint(Vec3 worldPoint) {
        return worldToLocalDirection(requireFinite(worldPoint, "worldPoint").subtract(worldOrigin));
    }

    /** Rotates a direction or displacement without translation or normalization. */
    public Vec3 localToWorldDirection(Vec3 localDirection) {
        return rotate(localDirection, rotationX, rotationY, rotationZ, rotationW);
    }

    public Vec3 worldToLocalDirection(Vec3 worldDirection) {
        return rotate(worldDirection, -rotationX, -rotationY, -rotationZ, rotationW);
    }

    /**
     * World-space velocity at a local point, in blocks/second. Linear velocity is measured at
     * the given local center of mass; angular velocity is world-space radians/second.
     */
    public Vec3 surfaceVelocity(
            Vec3 localPoint,
            Vec3 localCenterOfMass,
            Vec3 linearVelocityPerSecond,
            Vec3 angularVelocityPerSecond
    ) {
        requireFinite(localPoint, "localPoint");
        requireFinite(localCenterOfMass, "localCenterOfMass");
        requireFinite(linearVelocityPerSecond, "linearVelocityPerSecond");
        requireFinite(angularVelocityPerSecond, "angularVelocityPerSecond");
        var worldOffset = localToWorldDirection(localPoint.subtract(localCenterOfMass));
        return requireFinite(linearVelocityPerSecond.add(
                angularVelocityPerSecond.cross(worldOffset)), "surfaceVelocity");
    }

    private static Vec3 rotate(Vec3 value, double x, double y, double z, double w) {
        requireFinite(value, "vector");
        var twiceCrossX = 2.0 * (y * value.z - z * value.y);
        var twiceCrossY = 2.0 * (z * value.x - x * value.z);
        var twiceCrossZ = 2.0 * (x * value.y - y * value.x);
        return requireFinite(new Vec3(
                value.x + w * twiceCrossX + y * twiceCrossZ - z * twiceCrossY,
                value.y + w * twiceCrossY + z * twiceCrossX - x * twiceCrossZ,
                value.z + w * twiceCrossZ + x * twiceCrossY - y * twiceCrossX
        ), "rotated vector");
    }

    static Vec3 requireFinite(Vec3 value, String name) {
        Objects.requireNonNull(value, name);
        if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }
}
