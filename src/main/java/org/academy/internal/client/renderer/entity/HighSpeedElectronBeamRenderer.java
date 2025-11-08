package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;
import org.academy.api.client.Render;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.renderer.BallRenderer;
import org.academy.api.client.renderer.BoxRenderer;
import org.academy.api.client.util.VertexUtil;
import org.academy.internal.client.renderer.entity.state.HighSpeedElectronBeamRenderState;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.joml.Matrix4f;

public class HighSpeedElectronBeamRenderer extends EntityRenderer<HighSpeedElectronBeam, HighSpeedElectronBeamRenderState> {
    public static final float[][] HEAD_BUFFER = VertexUtil.Ball.getIcosphereVertexBuffer(1, 2, true);
    public static final AABB RAY = new AABB(-0.5, 0, -0.5, 0.5, 1, 0.5);

    public HighSpeedElectronBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void submit(HighSpeedElectronBeamRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        var ballRadius = renderState.progress * 0.185f;

        var commonInitialOrientation = new Matrix4f()
                .rotateY((float) Math.toRadians(90 - renderState.yRot))
                .rotateZ((float) Math.toRadians(90 + renderState.xRot));

        poseStack.pushPose();
        poseStack.mulPose(commonInitialOrientation);

        poseStack.pushPose();
        poseStack.mulPose(new Matrix4f().scale(ballRadius));
        BallRenderer.renderBall(
                poseStack.last(),
                BloomEffect.getBlitToMainPost().getBuffer(Render.RenderTypes.POS_COLOR_TRANGLES_BLOOM_POST),
                HEAD_BUFFER,
                0, 1, 0, 1,
                renderState.lightCoords, OverlayTexture.NO_OVERLAY
        );
        poseStack.scale(0.85f, 0.85f, 0.85f);
        nodeCollector.submitCustomGeometry(
                poseStack,
                Render.RenderTypes.POS_COLOR_TRANGLES,
                (pose, vertexConsumer) -> BallRenderer.renderBall(
                        pose,
                        vertexConsumer,
                        HEAD_BUFFER,
                        1, 1, 1, 1,
                        renderState.lightCoords, OverlayTexture.NO_OVERLAY
                )
        );
        poseStack.popPose();

        var rayVisualProgress = renderState.isCharging ? 0f : renderState.progress;

        poseStack.pushPose();
        poseStack.mulPose(new Matrix4f().scale(rayVisualProgress * 0.25f, renderState.length, rayVisualProgress * 0.25f));
        BoxRenderer.renderFilledBox(
                poseStack,
                BloomEffect.getBlitToMainPost().getBuffer(Render.RenderTypes.POS_COLOR_QUADS_BLOOM_POST),
                RAY, 0, 1, 0, 1
        );
        poseStack.scale(0.75f, 1, 0.75f);
        nodeCollector.submitCustomGeometry(
                poseStack,
                Render.RenderTypes.POS_COLOR_QUADS,
                (pose, vertexConsumer) -> BoxRenderer.renderFilledBox(
                        pose,
                        vertexConsumer,
                        RAY, 1, 1, 1, 1f
                )
        );
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
        reusedState.length = entity.length;
        reusedState.isCharging = entity.isCharging();

        float progress;
        if (entity.isCharging()) {
            progress = (entity.currentChargerTicks + partialTick) / HighSpeedElectronBeam.MAX_CHARGE_TICKS;
        } else {
            progress = (entity.currentRayLifeTicks - partialTick) / HighSpeedElectronBeam.MAX_RAY_LIFE_TICKS;
        }
        reusedState.yRot = entity.getYRot();
        reusedState.xRot = entity.getXRot();
        reusedState.progress = Math.max(0.0f, Math.min(1.0f, progress));
    }

    @Override
    protected boolean affectedByCulling(HighSpeedElectronBeam display) {
        return false;
    }
}