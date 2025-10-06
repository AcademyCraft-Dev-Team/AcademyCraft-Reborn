package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.phys.AABB;
import org.academy.api.client.render.post.PostEffect;
import org.academy.api.client.renderer.BoxRenderer;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.client.renderer.entity.state.HighSpeedElectronBeamRenderState;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.joml.Matrix4f;

public class HighSpeedElectronBeamRenderer extends EntityRenderer<HighSpeedElectronBeam, HighSpeedElectronBeamRenderState> {
    public static final AABB HEAD = new AABB(-1, -1, -1, 1, 1, 1);
    public static final AABB RAY = new AABB(-0.5, 0, -0.5, 0.5, 1, 0.5);

    @Override
    public void submit(HighSpeedElectronBeamRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        renderState.smoothProgress = MathUtil.lerpStartEndFactor(renderState.smoothProgress, renderState.progress, renderState.partialTick);

        var ballRadius = renderState.smoothProgress * 0.185f;

        var commonInitialOrientation = new Matrix4f()
                .rotateY((float) Math.toRadians(90 - renderState.yRot))
                .rotateZ((float) Math.toRadians(90 + renderState.xRot));

        poseStack.pushPose();
        poseStack.mulPose(commonInitialOrientation);

        poseStack.pushPose();
        poseStack.mulPose(new Matrix4f().scale(ballRadius));
        BoxRenderer.renderFilledBox(poseStack, PostEffect.BUFFER_SOURCE_PRE, HEAD, 0, 1, 0, 0.125f);
        poseStack.scale(0.5f, 0.5f, 0.5f);
        BoxRenderer.renderFilledBox(poseStack, PostEffect.BUFFER_SOURCE_PRE, HEAD, 1, 1, 1, 1f);
        poseStack.popPose();

        var rayVisualProgress = renderState.isCharging ? 0f : renderState.smoothProgress;

        poseStack.pushPose();
        poseStack.mulPose(new Matrix4f().scale(rayVisualProgress * 0.25f, renderState.length, rayVisualProgress * 0.25f));
        BoxRenderer.renderFilledBox(poseStack, PostEffect.BUFFER_SOURCE_PRE, RAY, 0, 1, 0, 0.125f);
        poseStack.scale(0.75f, 1, 0.75f);
        BoxRenderer.renderFilledBox(poseStack, PostEffect.BUFFER_SOURCE_PRE, RAY, 1, 1, 1, 1f);
        poseStack.popPose();

        poseStack.popPose();
    }

    @Override
    public HighSpeedElectronBeamRenderState createRenderState() {
        return new HighSpeedElectronBeamRenderState();
    }

    @Override
    public void extractRenderState(HighSpeedElectronBeam entity, HighSpeedElectronBeamRenderState reusedState, float partialTick) {
        super.extractRenderState(entity, reusedState, partialTick);
        reusedState.progress = entity.progress;
        reusedState.smoothProgress = entity.smoothProgress;
        reusedState.length = entity.length;
        reusedState.isCharging = entity.isCharging();
    }

    public HighSpeedElectronBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
}