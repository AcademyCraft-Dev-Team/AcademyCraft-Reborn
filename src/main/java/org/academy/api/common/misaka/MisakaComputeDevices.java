package org.academy.api.common.misaka;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global registry for Misaka compute devices.
 * Registration is process-wide; scanning always uses {@link #snapshot()}.
 */
public final class MisakaComputeDevices {
    private static final Map<Identifier, MisakaComputeDevice> BY_ID = new ConcurrentHashMap<>();
    private static volatile List<MisakaComputeDevice> SNAPSHOT = List.of();

    private MisakaComputeDevices() {
    }

    public static MisakaComputeDevice.Builder builder(Identifier id) {
        return new MisakaComputeDevice.Builder(id);
    }

    public static void register(MisakaComputeDevice device) {
        if (device == null) {
            throw new IllegalArgumentException("device");
        }
        var previous = BY_ID.putIfAbsent(device.id(), device);
        if (previous != null) {
            throw new IllegalStateException("Duplicate Misaka compute device id: " + device.id());
        }
        rebuildSnapshot();
        AcademyCraft.getLogger().debug("Registered Misaka compute device {}", device.id());
    }

    public static @Nullable MisakaComputeDevice get(Identifier id) {
        return id == null ? null : BY_ID.get(id);
    }

    /** Immutable copy for settle / tooltip iteration (safe if callbacks mutate the registry). */
    public static List<MisakaComputeDevice> snapshot() {
        return SNAPSHOT;
    }

    /** Client-side alias for tooltip injection. */
    public static List<MisakaComputeDevice> clientSnapshot() {
        return SNAPSHOT;
    }

    public static void clearForTesting() {
        BY_ID.clear();
        rebuildSnapshot();
    }

    private static void rebuildSnapshot() {
        SNAPSHOT = List.copyOf(new ArrayList<>(BY_ID.values()));
    }
}
