package org.academy.internal.common.structure.space;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Records vanilla storage failures that its save/load futures may otherwise consume.
 * Reporting can run on an I/O thread: it examines only the report's dimension key and never
 * queries a world. A failed slot stays failed until its entire server session is disposed.
 */
public final class NativeStructureStorageErrors {
    private static final Map<MinecraftServer, Integer> FAILURES = new IdentityHashMap<>();

    private NativeStructureStorageErrors() {
    }

    /** Called before the original vanilla diagnostic, which still logs and records the failure. */
    public static void reported(MinecraftServer server, RegionStorageInfo storageInfo) {
        if (storageInfo == null || storageInfo.dimension() == null) return;
        var slot = slot(storageInfo.dimension().identifier());
        if (slot < 0) return;
        synchronized (FAILURES) {
            FAILURES.merge(server, 1 << slot, (before, added) -> before | added);
        }
    }

    public static boolean failed(ServerLevel level) {
        var slot = slot(level.dimension().identifier());
        if (slot < 0) return false;
        synchronized (FAILURES) {
            return (FAILURES.getOrDefault(level.getServer(), 0) & (1 << slot)) != 0;
        }
    }

    /** Server shutdown cleanup only; loading a structure must never call this to retry a bad slot. */
    public static void clear(MinecraftServer server) {
        synchronized (FAILURES) {
            FAILURES.remove(server);
        }
    }

    private static int slot(Identifier dimension) {
        if (!dimension.getNamespace().equals("academy")) return -1;
        for (var slot = 0; slot < 4; slot++) {
            if (dimension.getPath().equals("structure_space_" + slot)) return slot;
        }
        return -1;
    }
}
