package org.academy.internal.common.sublevel.pipeline;

import net.minecraft.server.level.ServerLevel;
import org.academy.api.common.structure.StructureDynamics;
import org.academy.api.common.structure.StructurePipeline;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructureMotionState;
import org.academy.internal.common.structure.transaction.StructureCaptureCoordinator;

import java.util.List;
import java.util.UUID;

/**
 * Draft v2 bridge. The seven ports are implemented as bounded services, but no live capture,
 * physics, render or interaction path calls this instance and {@link SubLevelPipelineBootstrap}
 * keeps gameplay disabled. Wiring each port into production requires its own acceptance gate.
 */
public final class SubLevelPipelineDraft implements StructurePipeline {
    private final PipelineTerrainPublisher terrain = new PipelineTerrainPublisher();
    private final PipelineGeometryCompiler geometry = new PipelineGeometryCompiler();
    private final PipelineSubstepControl control = new PipelineSubstepControl();
    private final PipelineLiftController lift = new PipelineLiftController();
    private final PipelinePoseTimeline poses = new PipelinePoseTimeline();
    private final PipelineMeshPlanner meshes = new PipelineMeshPlanner();

    @Override
    public CaptureSlice advanceCapture(ServerLevel host, UUID transactionId, WorkLimit limit) {
        return StructureCaptureCoordinator.advance(host, transactionId, limit);
    }

    @Override
    public Geometry compileGeometry(GeometryInput input, WorkLimit limit) {
        return geometry.compile(input, limit);
    }

    @Override
    public TerrainReceipt publishTerrain(ServerLevel host, TerrainPublication publication) {
        return terrain.publish(host, publication);
    }

    @Override
    public SubstepForce controlSubstep(StructurePhysicsControl command, StructureMotionState motion, double seconds) {
        return control.control(command, motion, seconds);
    }

    @Override
    public LiftState advanceLift(LiftRequest request, LiftState previous, StructureDynamics.State actual,
                                 long serverTick) {
        return lift.advance(request, previous, actual, serverTick);
    }

    @Override
    public PoseSample samplePose(List<PoseFrame> history, double serverTick, double maximumExtrapolationTicks) {
        return poses.sample(history, serverTick, maximumExtrapolationTicks);
    }

    @Override
    public MeshPlan planMesh(MeshRequest request) {
        return meshes.plan(request);
    }

    /** Diagnostic access to the draft's coverage gate; not a gameplay capability. */
    public boolean terrainCoverageComplete(ServerLevel host, UUID runtimeId) {
        return terrain.coverageComplete(host, runtimeId);
    }

    public int cachedMeshEpochs() {
        return meshes.cachedEpochs();
    }

    public void clearTerrain(ServerLevel host) {
        terrain.clear(host);
    }
}
