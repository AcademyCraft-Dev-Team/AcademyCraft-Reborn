package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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

import static org.academy.api.client.render.Render.RenderTypes.POS_COLOR_QUADS_ADDITIVE_ALWAYS_VISIBLE;
import static org.academy.api.client.render.Render.RenderTypes.POS_COLOR_QUADS_ALWAYS_VISIBLE;
import static org.academy.api.client.render.Render.RenderTypes.POS_COLOR_QUADS_BLOOM_ADDITIVE_ALWAYS_VISIBLE;

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
                POS_COLOR_QUADS_ALWAYS_VISIBLE,
                (pose, consumer) -> renderBeamLayer(
                        pose.pose(), consumer, 0.78f, 0.48f, 0.02f, 0.92f
                )
        );

        poseStack.pushPose();
        poseStack.scale(0.62f, 1.002f, 0.62f);
        nodeCollector.submitCustomGeometry(
                poseStack,
                POS_COLOR_QUADS_ADDITIVE_ALWAYS_VISIBLE,
                (pose, consumer) -> renderBeamLayer(
                        pose.pose(), consumer, 1.0f, 0.72f, 0.18f, 0.92f
                )
        );
        nodeCollector.submitCustomGeometry(
                poseStack,
                POS_COLOR_QUADS_BLOOM_ADDITIVE_ALWAYS_VISIBLE,
                (pose, consumer) -> renderBeamLayer(
                        pose.pose(), consumer, 1.0f, 0.72f, 0.18f, 0.55f
                )
        );
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.scale(0.32f, 1.004f, 0.32f);
        nodeCollector.submitCustomGeometry(
                poseStack,
                POS_COLOR_QUADS_ADDITIVE_ALWAYS_VISIBLE,
                (pose, consumer) -> renderBeamLayer(
                        pose.pose(), consumer, 1.0f, 1.0f, 1.0f, 1.0f
                )
        );
        nodeCollector.submitCustomGeometry(
                poseStack,
                POS_COLOR_QUADS_BLOOM_ADDITIVE_ALWAYS_VISIBLE,
                (pose, consumer) -> renderBeamLayer(
                        pose.pose(), consumer, 1.0f, 1.0f, 1.0f, 0.8f
                )
        );
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.scale(0.18f, 1.006f, 0.18f);
        nodeCollector.submitCustomGeometry(
                poseStack,
                POS_COLOR_QUADS_ALWAYS_VISIBLE,
                (pose, consumer) -> renderBeamLayer(
                        pose.pose(), consumer, 1.0f, 1.0f, 1.0f, 0.98f
                )
        );
        poseStack.popPose();
        poseStack.popPose();
    }

    private static void renderBeamLayer(
            Matrix4f matrix, VertexConsumer consumer,
            float red, float green, float blue, float alpha
    ) {
        CylinderRenderer.renderCylinder(
                matrix, consumer, BUFFERED_VERTEX, red, green, blue, alpha
        );
        addVertex(matrix, consumer, -0.5f, 0.0f, 0.0f, red, green, blue, alpha);
        addVertex(matrix, consumer, 0.5f, 0.0f, 0.0f, red, green, blue, alpha);
        addVertex(matrix, consumer, 0.5f, 1.0f, 0.0f, red, green, blue, alpha);
        addVertex(matrix, consumer, -0.5f, 1.0f, 0.0f, red, green, blue, alpha);

        addVertex(matrix, consumer, 0.0f, 0.0f, -0.5f, red, green, blue, alpha);
        addVertex(matrix, consumer, 0.0f, 0.0f, 0.5f, red, green, blue, alpha);
        addVertex(matrix, consumer, 0.0f, 1.0f, 0.5f, red, green, blue, alpha);
        addVertex(matrix, consumer, 0.0f, 1.0f, -0.5f, red, green, blue, alpha);
    }

    private static void addVertex(
            Matrix4f matrix, VertexConsumer consumer,
            float x, float y, float z,
            float red, float green, float blue, float alpha
    ) {
        consumer.addVertex(matrix, x, y, z).setColor(red, green, blue, alpha);
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
