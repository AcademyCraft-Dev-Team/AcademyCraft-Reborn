package org.academy.internal.common.structure.physics;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Stable collision/configuration identity; it contains no runtime counters or registry numeric IDs. */
public final class NativeStructurePhysicsGeometryDigest {
    private static final byte[] PROFILE = ("academy-native-structure-shape-v1\0"
            + "rapier3d-f64-0.36.1;abi=1;density=1;friction=0.6;restitution=0;"
            + "linearDamping=0;angularDamping=0;gravityScale=1;ccd=1;canSleep=1\0")
            .getBytes(StandardCharsets.US_ASCII);

    private NativeStructurePhysicsGeometryDigest() {}

    public static String of(List<PhysicsBox> boxes) {
        Objects.requireNonNull(boxes, "boxes");
        if (boxes.size() > NativePhysicsWorld.MAX_BOXES_PER_BODY) {
            throw new IllegalArgumentException("A persisted structure exceeds the collision box budget");
        }
        var records = new ArrayList<double[]>(boxes.size());
        for (var box : boxes) {
            Objects.requireNonNull(box, "box");
            var center = box.center();
            var rotation = box.rotation();
            var extent = box.halfExtents();
            // All production boxes currently have identity rotation. Canonicalize q/-q as well
            // so this format does not distinguish two representations of the same orientation.
            double sign = rotation.w() < 0 || rotation.w() == 0 && (rotation.z() < 0
                    || rotation.z() == 0 && (rotation.y() < 0 || rotation.y() == 0 && rotation.x() < 0)) ? -1 : 1;
            records.add(new double[]{center.x(), center.y(), center.z(), rotation.x() * sign,
                    rotation.y() * sign, rotation.z() * sign, rotation.w() * sign,
                    extent.x(), extent.y(), extent.z()});
        }
        records.sort((first, second) -> {
            for (int index = 0; index < first.length; index++) {
                int order = Double.compare(zero(first[index]), zero(second[index]));
                if (order != 0) return order;
            }
            return 0;
        });
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(PROFILE);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(records.size()).array());
            var data = ByteBuffer.allocate(10 * Double.BYTES);
            for (var record : records) {
                data.clear();
                for (double value : record) data.putDouble(zero(value));
                digest.update(data.array());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("The JVM does not provide SHA-256", unavailable);
        }
    }

    private static double zero(double value) { return value == 0 ? 0 : value; }
}
