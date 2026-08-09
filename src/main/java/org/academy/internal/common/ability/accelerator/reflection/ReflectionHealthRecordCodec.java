package org.academy.internal.common.ability.accelerator.reflection;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/** Obfuscates reflection health snapshots kept in process memory. */
public final class ReflectionHealthRecordCodec {
    private static final int PROCESS_MASK = new SecureRandom().nextInt();

    public static String encode(UUID owner, float health) {
        var bits = Float.floatToRawIntBits(sanitize(health));
        var masked = bits ^ mask(owner);
        var bytes = new byte[]{
                (byte) (masked >>> 24),
                (byte) (masked >>> 16),
                (byte) (masked >>> 8),
                (byte) masked
        };
        return Base64.getEncoder().withoutPadding().encodeToString(bytes);
    }

    public static float decode(UUID owner, String encoded, float fallback) {
        if (encoded == null || encoded.isEmpty()) return sanitize(fallback);
        try {
            var bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length != Integer.BYTES) return sanitize(fallback);
            var masked = (bytes[0] & 0xFF) << 24
                    | (bytes[1] & 0xFF) << 16
                    | (bytes[2] & 0xFF) << 8
                    | bytes[3] & 0xFF;
            var decoded = Float.intBitsToFloat(masked ^ mask(owner));
            return Float.isFinite(decoded) ? Math.max(0.0f, decoded) : sanitize(fallback);
        } catch (IllegalArgumentException ignored) {
            return sanitize(fallback);
        }
    }

    public static float lockedHealth(float recorded, float original) {
        return Math.max(sanitize(recorded), sanitize(original));
    }

    public static float loweredHealth(float health, float amount) {
        var sanitized = sanitize(health);
        if (!Float.isFinite(amount) || !(amount > 0.0f)) return sanitized;
        return Math.max(0.0f, sanitized - amount);
    }

    private static float sanitize(float health) {
        return Float.isFinite(health) ? Math.max(0.0f, health) : 0.0f;
    }

    private static int mask(UUID owner) {
        var most = owner == null ? 0L : owner.getMostSignificantBits();
        var least = owner == null ? 0L : owner.getLeastSignificantBits();
        var mixed = most ^ Long.rotateLeft(least, 23);
        var mask = PROCESS_MASK ^ (int) (mixed ^ mixed >>> 32);
        return mask == 0 ? 0x6A09E667 : mask;
    }

    private ReflectionHealthRecordCodec() {
    }
}
