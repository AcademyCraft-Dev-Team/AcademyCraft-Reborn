package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.academy.api.client.renderer.CylinderRenderer;
import org.academy.api.client.util.VertexUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.client.renderer.entity.state.RailgunRayRenderState;
import org.academy.internal.common.world.entity.skill.RailgunRay;
import org.joml.Matrix4f;

import static org.academy.api.client.render.Render.RenderTypes.POS_COLOR_QUADS_BLOOM_ADDITIVE;
import static org.academy.api.client.render.Render.RenderTypes.POS_COLOR_QUADS_NO_DEPTH_WRITE;

public class RailgunRayRenderer extends EntityRenderer<RailgunRay, RailgunRayRenderState> {
    public static final float[][] BUFFERED_VERTEX = VertexUtil.Cylinder.getCylinderVertexBuffer(0, 1, 0.5f, 16, true);

    public RailgunRayRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void submit(RailgunRayRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        poseStack.pushPose();

        poseStack.mulPose(new Matrix4f()
                .rotateY((float) Math.toRadians(90 - renderState.yRot))
                .rotateZ((float) Math.toRadians(90 + renderState.xRot))
        );
        var progress = Math.max(0.0f, MathUtil.getFlatTopParabolaHeight(renderState.ageInTicks, 20, 5)) * 0.1f;
        poseStack.scale(progress, 50, progress);
        nodeCollector.submitCustomGeometry(
                poseStack,
                POS_COLOR_QUADS_NO_DEPTH_WRITE,
                (pose, consumer) -> CylinderRenderer.renderCylinder(
                        pose.pose(), consumer, BUFFERED_VERTEX, 0.78f, 0.48f, 0.02f, 0.92f
                )
        );

        poseStack.pushPose();
        poseStack.scale(0.54f, 1.002f, 0.54f);
        nodeCollector.submitCustomGeometry(
                poseStack,
                POS_COLOR_QUADS_BLOOM_ADDITIVE,
                (pose, consumer) -> CylinderRenderer.renderCylinder(
                        pose.pose(), consumer, BUFFERED_VERTEX, 1.0f, 0.72f, 0.18f, 0.72f
                )
        );
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.scale(0.31f, 1.004f, 0.31f);
        nodeCollector.submitCustomGeometry(
                poseStack,
                POS_COLOR_QUADS_BLOOM_ADDITIVE,
                (pose, consumer) -> CylinderRenderer.renderCylinder(
                        pose.pose(), consumer, BUFFERED_VERTEX, 1.0f, 1.0f, 1.0f, 1.0f
                )
        );
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.scale(0.20f, 1.006f, 0.20f);
        nodeCollector.submitCustomGeometry(
                poseStack,
                POS_COLOR_QUADS_NO_DEPTH_WRITE,
                (pose, consumer) -> CylinderRenderer.renderCylinder(
                        pose.pose(), consumer, BUFFERED_VERTEX, 1.0f, 1.0f, 1.0f, 0.98f
                )
        );
        poseStack.popPose();
        poseStack.popPose();
    }

    @Override
    public RailgunRayRenderState createRenderState() {
        return new RailgunRayRenderState();
    }

    @Override
    public void extractRenderState(RailgunRay entity, RailgunRayRenderState reusedState, float partialTick) {
        super.extractRenderState(entity, reusedState, partialTick);
        reusedState.xRot = entity.getXRot();
        reusedState.yRot = entity.getYRot();
    }

    @Override
    protected boolean affectedByCulling(RailgunRay display) {
        return false;
    }
}
