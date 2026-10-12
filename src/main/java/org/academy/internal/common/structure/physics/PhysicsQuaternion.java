package org.academy.internal.common.structure.physics;

/** An immutable unit quaternion, in the C ABI's x/y/z/w order. */
public record PhysicsQuaternion(double x, double y, double z, double w) {
    public static final PhysicsQuaternion IDENTITY = new PhysicsQuaternion(0, 0, 0, 1);

    public PhysicsQuaternion {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(w)) {
            throw new IllegalArgumentException("Physics quaternion must be finite");
        }
        double scale = Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.max(Math.abs(z), Math.abs(w)));
        if (scale == 0) throw new IllegalArgumentException("Physics quaternion has no usable orientation");
        x /= scale;
        y /= scale;
        z /= scale;
        w /= scale;
        double length = Math.sqrt(x * x + y * y + z * z + w * w);
        x /= length;
        y /= length;
        z /= length;
        w /= length;
    }
}
