package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.academy.internal.client.renderer.entity.state.NativeStructureProjectionRenderState;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/** A model-only renderer; inventories, block entity renderers, and physical ownership stay absent. */
public final class NativeStructureProjectionRenderer
        extends EntityRenderer<NativeStructureProjectionEntity, NativeStructureProjectionRenderState> {
    private static final boolean SMOKE_DIAGNOSTICS = Boolean.getBoolean("academy.nativeStructureProjectionSmoke");
    private static final Map<Integer, Submission> SMOKE_SUBMISSIONS = new HashMap<>();

    public NativeStructureProjectionRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public NativeStructureProjectionRenderState createRenderState() {
        return new NativeStructureProjectionRenderState();
    }

    @Override
    public void extractRenderState(NativeStructureProjectionEntity entity,
                                   NativeStructureProjectionRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        var pose = entity.renderPose(partialTick);
        // Vanilla position interpolation must not disagree with the pose used by exact picking.
        state.x = pose.worldOrigin().x;
        state.y = pose.worldOrigin().y;
        state.z = pose.worldOrigin().z;
        state.projectionEntityId = entity.getId();
        state.runtimeRevision = entity.viewState().revision();
        if (entity.level() instanceof ClientLevel level) state.updateBlocks(entity.view(), level, pose);
    }

    @Override
    public void submit(NativeStructureProjectionRenderState state, PoseStack poseStack,
                       SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        if (state.blocks.isEmpty()) return;
        poseStack.pushPose();
        var pose = state.pose;
        poseStack.mulPose(new Quaternionf((float) pose.rotationX(), (float) pose.rotationY(),
                (float) pose.rotationZ(), (float) pose.rotationW()).normalize());
        for (var cell : state.blocks) {
            var position = cell.localPosition();
            poseStack.pushPose();
            poseStack.translate(position.getX(), position.getY(), position.getZ());
            nodeCollector.submitMovingBlock(poseStack, cell.renderState(), state.outlineColor);
            poseStack.popPose();
        }
        poseStack.popPose();
        if (SMOKE_DIAGNOSTICS) {
            var previous = SMOKE_SUBMISSIONS.get(state.projectionEntityId);
            SMOKE_SUBMISSIONS.put(state.projectionEntityId, new Submission(
                    previous == null ? 1 : previous.frames() + 1, state.runtimeRevision));
        }
    }

    /** Test-only evidence of actual nonempty submit calls, disabled in ordinary clients. */
    public static long submittedFrames(int entityId) {
        var submission = SMOKE_SUBMISSIONS.get(entityId);
        return submission == null ? 0 : submission.frames();
    }

    public static long lastSubmittedRevision(int entityId) {
        var submission = SMOKE_SUBMISSIONS.get(entityId);
        return submission == null ? -1 : submission.revision();
    }

    private record Submission(long frames, long revision) {
    }
}
