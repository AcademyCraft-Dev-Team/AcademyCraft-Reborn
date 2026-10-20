package org.academy.api.common.structure;

import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Set;

/** Planned server-thread scheduling contract; no implementation or backend registration exists yet. */
public interface StructureSimulationBudget {
    enum Phase {
        TERRAIN_SCAN, TERRAIN_BUILD, GEOMETRY_BUILD, PHYSICS_STEP, CONTACTS, SNAPSHOT_ENCODE, PERSIST
    }

    enum DirtyPart {
        CONTENT, COLLISION, POSE, TERRAIN
    }

    /** Separate monotonic revisions; pose changes must not invalidate unchanged local geometry. */
    record DirtyVersion(long content, long collision, long pose, long terrain) {}

    /** Hard work limits and a measured wall-time target, all scoped to one host tick. */
    record Limits(long targetTickNanos, int terrainSections, int geometryCells,
                  int contactPairs, int snapshotBytes) {}

    record WorkItem(StructureRef reference, DirtyVersion version, boolean sleeping) {}

    record ScheduledWork(StructureRef reference, DirtyVersion version, Set<Phase> phases) {}

    record FramePlan(long serverTick, List<ScheduledWork> scheduled, List<WorkItem> deferred) {}

    /** elapsedNanos is measured work, never an inferred performance estimate. */
    record PhaseMetric(StructureRef reference, Phase phase, long elapsedNanos, long workUnits) {}

    record FrameMetrics(long serverTick, List<PhaseMetric> measurements) {}

    /** PERF-01: Plan bounded work without permitting stale collision geometry or dropping required solver steps. */
    FramePlan plan(ServerLevel host, long serverTick, List<WorkItem> work, Limits limits);

    /** PERF-02: Advance only the affected dirty revision after validating host and activation identity. */
    DirtyVersion invalidate(ServerLevel host, StructureRef reference, DirtyPart part);

    /** PERF-03: Record an actual stage duration and work count for the current server tick. */
    void record(ServerLevel host, StructureRef reference, Phase phase, long elapsedNanos, long workUnits);

    /** PERF-04: Read a bounded, immutable metrics snapshot; missing samples must remain distinguishable from zero. */
    FrameMetrics metrics(ServerLevel host, long serverTick);
}
