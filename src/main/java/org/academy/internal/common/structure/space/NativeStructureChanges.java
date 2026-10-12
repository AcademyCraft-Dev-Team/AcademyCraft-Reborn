package org.academy.internal.common.structure.space;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Local geometry changes are visible to queries before the end-of-tick dirty-cell drain. */
public final class NativeStructureChanges {
    private static final int MAX_DIRTY_POSITIONS = 8192;
    private static final Map<ServerLevel, Changes> LEVELS = new IdentityHashMap<>();

    public record ChangeBatch(boolean fullRescan, Set<BlockPos> positions) {
        public ChangeBatch {
            positions = Set.copyOf(positions);
        }
    }

    private NativeStructureChanges() {
    }

    public static void mark(ServerLevel level, BlockPos position) {
        requireThread(level);
        Objects.requireNonNull(position, "position");
        if (!NativeStructureDimensions.isStorageLevel(level.dimension())
                || !NativeStructureDimensions.contains(position) || level.isOutsideBuildHeight(position)) return;
        synchronized (LEVELS) {
            var changes = LEVELS.computeIfAbsent(level, ignored -> new Changes());
            changes.requireUsable();
            if (changes.revision == Long.MAX_VALUE) {
                changes.exhausted = true;
                throw new IllegalStateException("Native structure geometry revision exhausted");
            }
            changes.revision++;
            if (!changes.fullRescan) {
                changes.positions.add(position.immutable());
                if (changes.positions.size() > MAX_DIRTY_POSITIONS) {
                    changes.positions.clear();
                    changes.fullRescan = true;
                }
            }
        }
    }

    /** Drains cells, never the monotonic revision or its exhaustion state. */
    public static ChangeBatch take(ServerLevel level) {
        requireThread(level);
        synchronized (LEVELS) {
            var changes = LEVELS.get(level);
            if (changes == null) return new ChangeBatch(false, Set.of());
            changes.requireUsable();
            var result = new ChangeBatch(changes.fullRescan, changes.positions);
            changes.positions.clear();
            changes.fullRescan = false;
            return result;
        }
    }

    /** Lifecycle cleanup only; callers must invalidate every handle before resetting its level. */
    public static void clear(ServerLevel level) {
        requireThread(level);
        synchronized (LEVELS) {
            LEVELS.remove(level);
        }
    }

    public static long revision(ServerLevel level) {
        requireThread(level);
        synchronized (LEVELS) {
            var changes = LEVELS.get(level);
            if (changes == null) return 0;
            changes.requireUsable();
            return changes.revision;
        }
    }

    private static void requireThread(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Native structure change tracking requires the server thread");
        }
    }

    private static final class Changes {
        private final Set<BlockPos> positions = new HashSet<>();
        private long revision;
        private boolean exhausted;
        private boolean fullRescan;

        private void requireUsable() {
            if (exhausted) throw new IllegalStateException("Native structure geometry revision exhausted");
        }
    }
}
