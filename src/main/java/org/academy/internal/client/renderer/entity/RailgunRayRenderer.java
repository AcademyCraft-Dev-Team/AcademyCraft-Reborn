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

import static org.academy.api.client.render.Render.RenderTypes.POS_COLOR_QUADS_ADDITIVE;
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
        submitBeamSegments(nodeCollector, poseStack, renderState.visibleSegments,
                progress, POS_COLOR_QUADS_NO_DEPTH_WRITE, 0.78f, 0.48f, 0.02f, 0.92f);
        submitBeamSegments(nodeCollector, poseStack, renderState.visibleSegments,
                progress * 0.62f, POS_COLOR_QUADS_ADDITIVE, 1.0f, 0.72f, 0.18f, 0.92f);
        submitBeamSegments(nodeCollector, poseStack, renderState.visibleSegments,
                progress * 0.32f, POS_COLOR_QUADS_ADDITIVE, 1.0f, 1.0f, 1.0f, 1.0f);
        submitBeamSegments(nodeCollector, poseStack, renderState.visibleSegments,
                progress * 0.18f, POS_COLOR_QUADS_NO_DEPTH_WRITE, 1.0f, 1.0f, 1.0f, 0.98f);
        poseStack.popPose();
    }

    private static void submitBeamSegments(
            SubmitNodeCollector nodeCollector,
            PoseStack poseStack,
            float[] visibleSegments,
            float radius,
            net.minecraft.client.renderer.rendertype.RenderType renderType,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        if (radius <= 0.0f) return;
        for (var i = 0; i + 1 < visibleSegments.length; i += 2) {
            var start = visibleSegments[i];
            var end = visibleSegments[i + 1];
            if (end <= start) continue;
            poseStack.pushPose();
            poseStack.translate(0.0f, start, 0.0f);
            poseStack.scale(radius, end - start, radius);
            nodeCollector.submitCustomGeometry(
                    poseStack,
                    renderType,
                    (pose, consumer) -> renderBeamLayer(
                            pose.pose(), consumer, red, green, blue, alpha
                    )
            );
            poseStack.popPose();
        }
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
        reusedState.visibleSegments = BeamOcclusion.visibleSegments(
                entity,
                entity.position(),
                entity.getLookAngle(),
                50.0f,
                0.08
        );
    }

    @Override
    protected boolean affectedByCulling(RailgunRay display) {
        return false;
    }
}
