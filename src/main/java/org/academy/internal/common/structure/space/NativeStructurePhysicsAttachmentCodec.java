package org.academy.internal.common.structure.space;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.structure.physics.NativeStructurePhysicsAttachment;

import java.io.IOException;
import java.util.Set;

/** Strict, bounded metadata values; neither decoding nor encoding touches a native library. */
final class NativeStructurePhysicsAttachmentCodec {
    private static final Set<String> BOUNDS_KEYS = Set.of("min_x", "min_y", "min_z", "max_x", "max_y", "max_z");
    private static final Set<String> VECTOR_KEYS = Set.of("x", "y", "z");

    private NativeStructurePhysicsAttachmentCodec() {}

    static CompoundTag encode(NativeStructurePhysicsAttachment attachment) {
        var result = new CompoundTag();
        var bounds = new CompoundTag();
        bounds.putDouble("min_x", attachment.bounds().minX);
        bounds.putDouble("min_y", attachment.bounds().minY);
        bounds.putDouble("min_z", attachment.bounds().minZ);
        bounds.putDouble("max_x", attachment.bounds().maxX);
        bounds.putDouble("max_y", attachment.bounds().maxY);
        bounds.putDouble("max_z", attachment.bounds().maxZ);
        result.put("bounds", bounds);
        result.putBoolean("resume_allowed", attachment.resumeAllowed());
        if (attachment.motion() != null) {
            var motion = attachment.motion();
            var values = new CompoundTag();
            values.put("linear_velocity", encodeVector(motion.linearVelocity()));
            values.put("angular_velocity", encodeVector(motion.angularVelocity()));
            values.put("local_center_of_mass", encodeVector(motion.localCenterOfMass()));
            values.putBoolean("sleeping", motion.sleeping());
            values.putString("geometry_digest", motion.geometryDigest());
            result.put("motion", values);
        }
        return result;
    }

    static NativeStructurePhysicsAttachment decode(CompoundTag tag) throws IOException {
        boolean hasMotion = tag.get("motion") != null;
        requireKeys(tag, hasMotion ? Set.of("bounds", "resume_allowed", "motion") : Set.of("bounds", "resume_allowed"));
        var bounds = compound(tag, "bounds");
        requireKeys(bounds, BOUNDS_KEYS);
        double minX = number(bounds, "min_x"), minY = number(bounds, "min_y"), minZ = number(bounds, "min_z");
        double maxX = number(bounds, "max_x"), maxY = number(bounds, "max_y"), maxZ = number(bounds, "max_z");
        // AABB normalizes endpoints; reject inverted on-disk bounds before it can repair them.
        if (!(minX < maxX && minY < maxY && minZ < maxZ)) throw new IOException("Invalid saved physics bounds order");
        NativeStructurePhysicsAttachment.Motion motion = null;
        if (hasMotion) {
            var values = compound(tag, "motion");
            requireKeys(values, Set.of("linear_velocity", "angular_velocity", "local_center_of_mass", "sleeping", "geometry_digest"));
            requireType(values, "geometry_digest", Tag.TAG_STRING);
            motion = new NativeStructurePhysicsAttachment.Motion(vector(values, "linear_velocity"),
                    vector(values, "angular_velocity"), vector(values, "local_center_of_mass"),
                    flag(values, "sleeping"), values.getString("geometry_digest").orElseThrow());
        }
        return new NativeStructurePhysicsAttachment(new AABB(minX, minY, minZ, maxX, maxY, maxZ),
                motion, flag(tag, "resume_allowed"));
    }

    private static CompoundTag encodeVector(Vec3 value) {
        var result = new CompoundTag();
        result.putDouble("x", value.x);
        result.putDouble("y", value.y);
        result.putDouble("z", value.z);
        return result;
    }

    private static Vec3 vector(CompoundTag parent, String key) throws IOException {
        var value = compound(parent, key);
        requireKeys(value, VECTOR_KEYS);
        return new Vec3(number(value, "x"), number(value, "y"), number(value, "z"));
    }

    private static CompoundTag compound(CompoundTag parent, String key) throws IOException {
        requireType(parent, key, Tag.TAG_COMPOUND);
        return parent.getCompound(key).orElseThrow();
    }

    private static double number(CompoundTag parent, String key) throws IOException {
        requireType(parent, key, Tag.TAG_DOUBLE);
        var value = parent.getDouble(key).orElseThrow();
        if (!Double.isFinite(value)) throw new IOException("Non-finite saved physics field: " + key);
        return value;
    }

    private static boolean flag(CompoundTag parent, String key) throws IOException {
        requireType(parent, key, Tag.TAG_BYTE);
        var value = parent.getByte(key).orElseThrow();
        if (value != 0 && value != 1) throw new IOException("Invalid saved physics flag: " + key);
        return value == 1;
    }

    private static void requireType(CompoundTag parent, String key, byte type) throws IOException {
        var value = parent.get(key);
        if (value == null || value.getId() != type) throw new IOException("Missing or invalid saved physics field: " + key);
    }

    private static void requireKeys(CompoundTag tag, Set<String> expected) throws IOException {
        if (!tag.keySet().equals(expected)) throw new IOException("Unexpected saved physics fields");
    }
}
