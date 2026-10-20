package org.academy.internal.common.sublevel;

import net.minecraft.server.level.ServerLevel;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureSimulationBudget;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Per-host simulation scheduler and measured phase metrics. Dirty revisions are independent so
 * a pose-only change never forces a geometry rebuild. Measurements are the only source of
 * elapsed time; an unsampled tick reports an empty frame instead of a fabricated zero.
 */
public final class SubLevelSimulationBudget implements StructureSimulationBudget {
    public static final int MAX_METRIC_TICKS = 200;
    public static final int MAX_METRICS_PER_TICK = 512;
    private static final Map<ServerLevel, HostState> HOSTS = new WeakHashMap<>();

    /** Fresh version for one reference; a missing reference starts clean. */
    public synchronized DirtyVersion version(ServerLevel host, StructureRef reference) {
        requireHost(host);
        return state(host).version(reference);
    }

    @Override
    public synchronized FramePlan plan(ServerLevel host, long serverTick, List<WorkItem> work, Limits limits) {
        requireHost(host);
        Objects.requireNonNull(work, "work");
        var checked = requireLimits(limits);
        var scheduled = new ArrayList<ScheduledWork>();
        var deferred = new ArrayList<WorkItem>();
        // The plan runs before this tick's phases are recorded, so the latest complete frame is
        // the previous tick. An unsampled frame contributes nothing and is not a fake zero.
        long measured = 0;
        for (var metric : state(host).metricsFor(serverTick - 1)) measured += metric.elapsedNanos();
        boolean overBudget = measured > checked.targetTickNanos();
        int structures = 0;
        for (var item : work) {
            if (item == null) continue;
            if (!host.dimension().identifier().equals(item.reference().dimension())) {
                deferred.add(item);
                continue;
            }
            if (++structures > Math.max(1, checked.contactPairs())) {
                deferred.add(item);
                continue;
            }
            var current = state(host).version(item.reference());
            var supplied = item.version();
            boolean contentDirty = supplied.content() != current.content();
            boolean collisionDirty = supplied.collision() != current.collision();
            boolean poseDirty = supplied.pose() != current.pose();
            boolean terrainDirty = supplied.terrain() != current.terrain();
            var phases = EnumSet.of(Phase.CONTACTS);
            if (!item.sleeping() || poseDirty) phases.add(Phase.PHYSICS_STEP);
            if (poseDirty || !item.sleeping()) phases.add(Phase.GEOMETRY_BUILD);
            if (terrainDirty || !item.sleeping()) phases.add(Phase.TERRAIN_SCAN);
            if (contentDirty || collisionDirty) {
                phases.add(Phase.TERRAIN_BUILD);
                phases.add(Phase.GEOMETRY_BUILD);
            }
            if (!overBudget && (!item.sleeping() || poseDirty)) phases.add(Phase.SNAPSHOT_ENCODE);
            if (serverTick % 100 == 0) phases.add(Phase.PERSIST);
            scheduled.add(new ScheduledWork(item.reference(), current, Set.copyOf(phases)));
            if (phases.size() == 1 && item.sleeping() && !poseDirty) deferred.add(item);
        }
        return new FramePlan(serverTick, List.copyOf(scheduled), List.copyOf(deferred));
    }

    @Override
    public synchronized DirtyVersion invalidate(ServerLevel host, StructureRef reference, DirtyPart part) {
        requireHost(host);
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(part, "part");
        if (!host.dimension().identifier().equals(reference.dimension())) {
            throw new IllegalArgumentException("Structure reference belongs to another host dimension");
        }
        var state = state(host);
        var current = state.version(reference);
        var next = switch (part) {
            case CONTENT -> new DirtyVersion(current.content() + 1, current.collision(), current.pose(), current.terrain());
            case COLLISION -> new DirtyVersion(current.content(), current.collision() + 1, current.pose(), current.terrain());
            case POSE -> new DirtyVersion(current.content(), current.collision(), current.pose() + 1, current.terrain());
            case TERRAIN -> new DirtyVersion(current.content(), current.collision(), current.pose(), current.terrain() + 1);
        };
        state.putVersion(reference, next);
        return next;
    }

    @Override
    public synchronized void record(ServerLevel host, StructureRef reference, Phase phase, long elapsedNanos, long workUnits) {
        requireHost(host);
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(phase, "phase");
        if (elapsedNanos < 0 || workUnits < 0) return;
        state(host).record(host.getGameTime(), new PhaseMetric(reference, phase, elapsedNanos, workUnits));
    }

    @Override
    public synchronized FrameMetrics metrics(ServerLevel host, long serverTick) {
        requireHost(host);
        return new FrameMetrics(serverTick, state(host).metricsFor(serverTick));
    }

    /** Server shutdown: no measured state or dirty revision survives into a new world instance. */
    public synchronized void closeHost(ServerLevel host) {
        if (host != null) HOSTS.remove(host);
    }

    /** Drops the revisions and measurements of one removed or replaced structure. */
    public synchronized void forget(ServerLevel host, StructureRef reference) {
        var state = host == null ? null : HOSTS.get(host);
        if (state != null) state.forget(reference);
    }

    public synchronized void clear() {
        HOSTS.clear();
    }

    private static Limits requireLimits(Limits limits) {
        Objects.requireNonNull(limits, "limits");
        if (limits.targetTickNanos() <= 0 || limits.terrainSections() < 0 || limits.geometryCells() < 0
                || limits.contactPairs() <= 0 || limits.snapshotBytes() < 0) {
            throw new IllegalArgumentException("Simulation limits must be positive and bounded");
        }
        return limits;
    }

    private static void requireHost(ServerLevel host) {
        Objects.requireNonNull(host, "host");
        if (host.getServer() == null || !host.getServer().isSameThread()) {
            throw new IllegalStateException("Simulation budget requires the owning server thread");
        }
        if (host.getServer().getLevel(host.dimension()) != host) {
            throw new IllegalArgumentException("Simulation host is not the registered server level");
        }
    }

    private static synchronized HostState state(ServerLevel host) {
        return HOSTS.computeIfAbsent(host, ignored -> new HostState());
    }

    private static final class HostState {
        private final Map<StructureRef, DirtyVersion> versions = new HashMap<>();
        private final LinkedHashMap<Long, List<PhaseMetric>> metrics = new LinkedHashMap<>();

        private DirtyVersion version(StructureRef reference) {
            return versions.getOrDefault(reference, new DirtyVersion(0, 0, 0, 0));
        }

        private void putVersion(StructureRef reference, DirtyVersion version) {
            versions.put(reference, version);
        }

        private void record(long tick, PhaseMetric metric) {
            var list = metrics.computeIfAbsent(tick, ignored -> new ArrayList<>());
            if (list.size() >= MAX_METRICS_PER_TICK) return;
            list.add(metric);
            if (metrics.size() > MAX_METRIC_TICKS) {
                var iterator = metrics.keySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
        }

        private List<PhaseMetric> metricsFor(long tick) {
            var list = metrics.get(tick);
            return list == null ? List.of() : List.copyOf(list);
        }

        private void forget(StructureRef reference) {
            versions.remove(reference);
        }
    }
}
