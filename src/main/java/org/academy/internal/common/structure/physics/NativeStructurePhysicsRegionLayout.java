package org.academy.internal.common.structure.physics;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.structure.StructurePhysics;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;

/** Shared limits for persisted and live regions that are solved in independent native worlds. */
public final class NativeStructurePhysicsRegionLayout {
    public static final int MAX_REGIONS = 3;
    public static final double MIN_SEPARATION = 1.0;
    private static final byte[] LEGACY_NAMESPACE = "academy-native-physics-v2-region\0".getBytes(StandardCharsets.US_ASCII);

    private NativeStructurePhysicsRegionLayout() {}

    public record Region(UUID id, Identifier host, AABB bounds) {
        public Region {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(host, "host");
            validateBounds(bounds);
        }
    }

    /** Repeated members of one region are allowed; conflicting identities or nearby worlds are not. */
    public static void validate(Collection<Region> regions) {
        Objects.requireNonNull(regions, "regions");
        var unique = new LinkedHashMap<UUID, Region>();
        for (var region : regions) {
            Objects.requireNonNull(region, "region");
            var previous = unique.get(region.id());
            if (previous != null) {
                if (!previous.equals(region)) throw new IllegalArgumentException("A physics region UUID has conflicting host or bounds");
                continue;
            }
            if (unique.size() == MAX_REGIONS) throw new IllegalArgumentException("Too many physics regions");
            for (var other : unique.values()) {
                if (region.host().equals(other.host()) && !separated(region.bounds(), other.bounds())) {
                    throw new IllegalArgumentException("Independent physics regions in one host require a one-block gap");
                }
            }
            unique.put(region.id(), region);
        }
    }

    /** Stable migration identity; neither map iteration order nor runtime/session identity enters it. */
    public static UUID legacyId(Identifier host, AABB bounds) {
        Objects.requireNonNull(host, "host");
        validateBounds(bounds);
        var encodedHost = host.toString().getBytes(StandardCharsets.UTF_8);
        var bytes = ByteBuffer.allocate(LEGACY_NAMESPACE.length + Integer.BYTES + encodedHost.length + 6 * Double.BYTES);
        bytes.put(LEGACY_NAMESPACE).putInt(encodedHost.length).put(encodedHost);
        for (double value : new double[]{bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ}) {
            bytes.putDouble(value == 0 ? 0 : value);
        }
        return UUID.nameUUIDFromBytes(bytes.array());
    }

    static void validateBounds(AABB bounds) {
        Objects.requireNonNull(bounds, "bounds");
        for (double value : new double[]{bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ}) {
            PhysicsLimits.range(value, -29_999_980, 29_999_980, "saved region coordinate");
        }
        for (double size : new double[]{bounds.getXsize(), bounds.getYsize(), bounds.getZsize()}) {
            if (!Double.isFinite(size) || size <= 0 || size > StructurePhysics.MAX_REGION_AXIS) {
                throw new IllegalArgumentException("Saved physics region axes must be in (0, 24]");
            }
        }
    }

    private static boolean separated(AABB first, AABB second) {
        return first.minX - second.maxX >= MIN_SEPARATION || second.minX - first.maxX >= MIN_SEPARATION
                || first.minY - second.maxY >= MIN_SEPARATION || second.minY - first.maxY >= MIN_SEPARATION
                || first.minZ - second.maxZ >= MIN_SEPARATION || second.minZ - first.maxZ >= MIN_SEPARATION;
    }
}
