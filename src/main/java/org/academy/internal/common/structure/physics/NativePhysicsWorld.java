package org.academy.internal.common.structure.physics;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** One bounded origin-relative physics island. Not attached to Minecraft storage or entities. */
public final class NativePhysicsWorld implements AutoCloseable {
    public static final int MAX_BODIES = 256;
    public static final int MAX_BOXES_PER_BODY = 256;
    public static final int MAX_BOXES = 32_768;
    private final NativePhysicsLibrary library;
    private final PhysicsNativeAccess access;
    private final long handle;
    private final PhysicsVector origin;
    private final Set<NativePhysicsBody> bodies = new LinkedHashSet<>();
    private int boxes;
    private boolean failed;
    private boolean closed;

    NativePhysicsWorld(NativePhysicsLibrary library, PhysicsNativeAccess access, long handle, PhysicsVector origin) {
        if (handle == 0) throw new IllegalStateException("Native physics returned a null world");
        this.library = library;
        this.access = access;
        this.handle = handle;
        this.origin = origin;
    }

    public PhysicsVector origin() {
        requireOpen();
        return origin;
    }

    public NativePhysicsBody createBody(BodyType type, PhysicsPose pose, List<PhysicsBox> cuboids,
                                        double density, double friction, double restitution, boolean ccd) {
        requireOpen();
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(cuboids, "cuboids");
        int count = cuboids.size();
        if (count == 0 || count > MAX_BOXES_PER_BODY) throw new IllegalArgumentException("Native body requires 1 to 256 cuboids");
        if (bodies.size() >= MAX_BODIES || boxes + count > MAX_BOXES) {
            throw new IllegalStateException("Native physics world body/cuboid limit reached");
        }
        var shapes = List.copyOf(cuboids);
        PhysicsLimits.range(density, 0, 1.0E9, "density");
        if (type == BodyType.DYNAMIC && density == 0) throw new IllegalArgumentException("Dynamic bodies require positive density");
        PhysicsLimits.range(friction, 0, 10, "friction");
        PhysicsLimits.range(restitution, 0, 1, "restitution");
        var description = new double[21];
        System.arraycopy(relativePose(pose), 0, description, 0, 7);
        description[13] = density;
        description[14] = friction;
        description[15] = restitution;
        description[18] = 1;
        description[19] = ccd ? 1 : 0;
        description[20] = 1;
        var data = new double[count * 10];
        for (int index = 0; index < shapes.size(); index++) {
            var shape = shapes.get(index);
            int offset = index * 10;
            var center = shape.center();
            var rotation = shape.rotation();
            var extent = shape.halfExtents();
            data[offset] = center.x(); data[offset + 1] = center.y(); data[offset + 2] = center.z();
            data[offset + 3] = rotation.x(); data[offset + 4] = rotation.y();
            data[offset + 5] = rotation.z(); data[offset + 6] = rotation.w();
            data[offset + 7] = extent.x(); data[offset + 8] = extent.y(); data[offset + 9] = extent.z();
        }
        long bodyHandle = call(() -> access.createBody(handle, type.code, description, data));
        if (bodyHandle == 0) {
            failed = true;
            throw new IllegalStateException("Native physics returned a null body");
        }
        var body = new NativePhysicsBody(this, bodyHandle, type, count, ccd);
        bodies.add(body);
        boxes += count;
        return body;
    }

    /** Welds two dynamic bodies of this world together at their local anchors. */
    public void createFixedJoint(NativePhysicsBody first, NativePhysicsBody second,
                                 PhysicsVector firstAnchor, PhysicsVector secondAnchor) {
        requireOpen();
        requireDynamic(first);
        requireDynamic(second);
        if (first == second) throw new IllegalArgumentException("A fixed joint requires two distinct bodies");
        PhysicsLimits.vector(Objects.requireNonNull(firstAnchor, "firstAnchor"), 1.0E6, "first joint anchor");
        PhysicsLimits.vector(Objects.requireNonNull(secondAnchor, "secondAnchor"), 1.0E6, "second joint anchor");
        run(() -> access.createFixedJoint(handle, first.handle, second.handle, new double[]{
                firstAnchor.x(), firstAnchor.y(), firstAnchor.z(),
                secondAnchor.x(), secondAnchor.y(), secondAnchor.z()}));
    }

    public void step(double duration, int substeps) {
        requireOpen();
        if (!Double.isFinite(duration) || duration <= 0 || duration > 0.1 || substeps < 1 || substeps > 16
                || duration / substeps < 1.0E-6) {
            throw new IllegalArgumentException("Physics step requires dt in (0, 0.1], 1..16 substeps and substep >= 1e-6 seconds");
        }
        run(() -> access.step(handle, duration, substeps));
    }

    PhysicsBodyState state(NativePhysicsBody body) {
        requireBody(body);
        return call(() -> {
            var data = access.state(handle, body.handle);
            if (data.length != 26 || data[25] != body.type.code || data[24] != 0 && data[24] != 1) {
                throw new IllegalStateException("Malformed native physics state");
            }
            for (double value : data) if (!Double.isFinite(value)) throw new IllegalStateException("Non-finite native physics state");
            return new PhysicsBodyState(new PhysicsPose(vector(data, 0).add(origin), quaternion(data, 3)),
                    vector(data, 7), vector(data, 10), data[13], vector(data, 14), vector(data, 17),
                    quaternion(data, 20), data[24] == 1, body.type);
        });
    }

    void setPose(NativePhysicsBody body, PhysicsPose pose) {
        requireBody(body);
        var values = relativePose(Objects.requireNonNull(pose, "pose"));
        run(() -> access.setPose(handle, body.handle, values));
    }

    public void setVelocity(NativePhysicsBody body, PhysicsVector linear, PhysicsVector angular) {
        requireDynamic(body);
        PhysicsLimits.vector(Objects.requireNonNull(linear, "linear"), 1.0E4, "linear velocity");
        PhysicsLimits.vector(Objects.requireNonNull(angular, "angular"), 1.0E4, "angular velocity");
        run(() -> access.setVelocity(handle, body.handle,
                new double[]{linear.x(), linear.y(), linear.z(), angular.x(), angular.y(), angular.z()}));
    }

    public void impulse(NativePhysicsBody body, PhysicsVector impulse, PhysicsVector worldPoint) {
        requireDynamic(body);
        PhysicsLimits.vector(Objects.requireNonNull(impulse, "impulse"), 1.0E12, "impulse");
        var point = Objects.requireNonNull(worldPoint, "worldPoint").subtract(origin);
        PhysicsLimits.vector(point, 1.0E6, "relative impulse point");
        run(() -> access.impulse(handle, body.handle,
                new double[]{impulse.x(), impulse.y(), impulse.z(), point.x(), point.y(), point.z()}));
    }

    void sleep(NativePhysicsBody body) { requireDynamic(body); run(() -> access.sleep(handle, body.handle)); }
    void wake(NativePhysicsBody body) { requireDynamic(body); run(() -> access.wake(handle, body.handle)); }

    void remove(NativePhysicsBody body) {
        library.requireThread();
        if (body.removed) return;
        requireBody(body);
        run(() -> access.removeBody(handle, body.handle));
        body.removed = true;
        bodies.remove(body);
        boxes -= body.boxCount;
    }

    void requireBody(NativePhysicsBody body) {
        requireOpen();
        if (body.removed || !bodies.contains(body)) throw new IllegalStateException("Native physics body is no longer alive");
    }

    private void requireDynamic(NativePhysicsBody body) {
        requireBody(body);
        if (body.type != BodyType.DYNAMIC) throw new IllegalArgumentException("Operation requires a dynamic body");
    }

    private double[] relativePose(PhysicsPose pose) {
        PhysicsLimits.vector(pose.position().subtract(origin), 1.0E6, "relative body position");
        return pose.relativeArray(origin);
    }

    private void requireOpen() {
        library.requireOpen();
        if (closed) throw new IllegalStateException("Native physics world is closed");
        if (failed) throw new IllegalStateException("Native physics world failed; close it instead of retrying");
    }

    private <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException | Error failure) {
            if (!(failure instanceof NativePhysicsException nativeFailure)
                    || nativeFailure.status() != 1 && nativeFailure.status() != 3) failed = true;
            throw failure;
        }
    }

    private void run(Runnable action) {
        call(() -> { action.run(); return null; });
    }

    @Override
    public void close() {
        library.requireThread();
        if (closed) return;
        try {
            access.destroyWorld(handle);
        } catch (RuntimeException | Error failure) {
            failed = true;
            throw failure;
        }
        closed = true;
        for (var body : bodies) body.removed = true;
        bodies.clear();
        boxes = 0;
        library.removed(this);
    }

    private static PhysicsVector vector(double[] values, int offset) {
        return new PhysicsVector(values[offset], values[offset + 1], values[offset + 2]);
    }

    private static PhysicsQuaternion quaternion(double[] values, int offset) {
        return new PhysicsQuaternion(values[offset], values[offset + 1], values[offset + 2], values[offset + 3]);
    }
}
