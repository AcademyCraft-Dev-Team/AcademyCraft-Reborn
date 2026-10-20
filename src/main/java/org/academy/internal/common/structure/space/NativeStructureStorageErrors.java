package org.academy.internal.common.structure.space;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * Records vanilla storage failures that its save/load futures may otherwise consume.
 * Reporting can run on an I/O thread: it examines only the report's dimension key and never
 * queries a world. A failed dimension stays failed until its entire server session is disposed.
 */
public final class NativeStructureStorageErrors {
    private static final Map<MinecraftServer, Set<Identifier>> FAILURES = new IdentityHashMap<>();

    private NativeStructureStorageErrors() {
    }

    /** Called before the original vanilla diagnostic, which still logs and records the failure. */
    public static void reported(MinecraftServer server, RegionStorageInfo storageInfo) {
        if (storageInfo == null || storageInfo.dimension() == null) return;
        synchronized (FAILURES) {
            FAILURES.computeIfAbsent(server, ignored -> new HashSet<>()).add(storageInfo.dimension().identifier());
        }
    }

    public static boolean failed(ServerLevel level) {
        synchronized (FAILURES) {
            return FAILURES.getOrDefault(level.getServer(), Set.of()).contains(level.dimension().identifier());
        }
    }

    /** Server shutdown cleanup only; loading a structure must never call this to retry a bad slot. */
    public static void clear(MinecraftServer server) {
        synchronized (FAILURES) {
            FAILURES.remove(server);
        }
    }

}
