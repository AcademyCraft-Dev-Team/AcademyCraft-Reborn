package org.academy.internal.common.sublevel.pipeline;

import org.academy.api.common.structure.StructurePipeline;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PIPE-07 draft mesh planner. Content, lighting and resource revisions are independent: a pose
 * update reuses every section, a content/resource change requests a full rebuild, and a lighting
 * change rebuilds only the sections the caller marked dirty. Small structures (9..128 cells) use
 * the same cache; there is no 512-cell bypass. Block entities are always a separate draw pass.
 */
public final class PipelineMeshPlanner {
    public static final int MAX_CACHED_EPOCHS = 512;

    private final Map<StructurePipeline.Epoch, MeshState> states =
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<StructurePipeline.Epoch, MeshState> eldest) {
                    return size() > MAX_CACHED_EPOCHS;
                }
            };

    public synchronized StructurePipeline.MeshPlan plan(StructurePipeline.MeshRequest request) {
        if (request == null) throw new IllegalArgumentException("Mesh request is required");
        var epoch = request.epoch();
        if (epoch == null || epoch.reference() == null || epoch.runtimeId() == null || epoch.revision() < 0) {
            throw new IllegalArgumentException("Mesh epoch is invalid");
        }
        if (request.lightingRevision() < 0 || request.resourceEpoch() < 0 || request.cellCount() < 0) {
            throw new IllegalArgumentException("Mesh revisions and cell count must be nonnegative");
        }
        for (var section : request.dirtySections()) {
            if (section == null) throw new IllegalArgumentException("Dirty section key is required");
        }
        boolean blockEntities = request.containsBlockEntities();
        if (request.cellCount() == 0) {
            states.put(epoch, new MeshState(epoch.revision(), request.lightingRevision(),
                    request.resourceEpoch(), request.cellCount(), blockEntities));
            return new StructurePipeline.MeshPlan(List.of(), true, blockEntities);
        }
        var previous = states.get(epoch);
        boolean contentChanged = previous == null || previous.revision != epoch.revision()
                || previous.cells == 0 && request.cellCount() > 0;
        boolean resourceChanged = previous != null && previous.resource != request.resourceEpoch();
        boolean lightingChanged = previous != null && previous.lighting != request.lightingRevision();
        boolean dirty = !request.dirtySections().isEmpty();

        StructurePipeline.MeshPlan plan;
        if (contentChanged || resourceChanged) {
            // Empty rebuild list with reuseGeometry=false is the full-rebuild signal.
            plan = new StructurePipeline.MeshPlan(List.of(), false, blockEntities);
        } else if (lightingChanged) {
            // A lighting-only change should not recompile geometry. The caller supplies the
            // light-dirty sections; without them a correct full rebuild is the conservative choice.
            plan = dirty
                    ? new StructurePipeline.MeshPlan(request.dirtySections(), true, blockEntities)
                    : new StructurePipeline.MeshPlan(List.of(), false, blockEntities);
        } else if (dirty) {
            plan = new StructurePipeline.MeshPlan(request.dirtySections(), false, blockEntities);
        } else {
            plan = new StructurePipeline.MeshPlan(List.of(), true, blockEntities);
        }
        states.put(epoch, new MeshState(epoch.revision(), request.lightingRevision(),
                request.resourceEpoch(), request.cellCount(), blockEntities));
        return plan;
    }

    /** Test/diagnostic bound: number of cached epochs, never the number of meshes. */
    public synchronized int cachedEpochs() {
        return states.size();
    }

    public synchronized void clear() {
        states.clear();
    }

    private record MeshState(long revision, long lighting, long resource, int cells, boolean blockEntities) {
    }
}
