package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.academy.api.client.renderer.CylinderRenderer;
import org.academy.api.client.util.VertexUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.client.renderer.entity.state.RailgunRayRenderState;
import org.academy.internal.common.world.entity.skill.RailgunRay;
import org.joml.Matrix4f;

import static org.academy.api.client.render.Render.RenderTypes.POS_COLOR_QUADS_ADDITIVE;
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
        var originalLength = ReflectedBeamVisualGeometry.safeLength(renderState.length);
        var outgoingLength = renderState.reflectionActive
                ? Math.min(originalLength, ReflectedBeamVisualGeometry.safeLength(renderState.reflectionDistance))
                : originalLength;
        submitBeamShellLayers(nodeCollector, poseStack, outgoingLength, progress);

        if (renderState.reflectionActive) {
            poseStack.pushPose();
            poseStack.translate(0.0f, outgoingLength, 0.0f);
            poseStack.mulPose(new Matrix4f().rotateZ((float) Math.PI));
            submitBeamShellLayers(nodeCollector, poseStack, originalLength, progress * 1.08f);
            poseStack.popPose();
        }

        submitBeamCoreLayers(nodeCollector, poseStack, outgoingLength, progress);

        if (renderState.reflectionActive) {
            poseStack.pushPose();
            poseStack.translate(0.0f, outgoingLength, 0.0f);
            poseStack.mulPose(new Matrix4f().rotateZ((float) Math.PI));
            submitBeamCoreLayers(nodeCollector, poseStack, originalLength, progress * 1.08f);
            poseStack.popPose();

            submitReflectionHighlight(nodeCollector, poseStack, outgoingLength, progress);
        }
        poseStack.popPose();
    }

    private static void submitBeamShellLayers(
            SubmitNodeCollector nodeCollector,
            PoseStack poseStack,
            float length,
            float progress
    ) {
        submitBeam(nodeCollector, poseStack, length,
                progress, POS_COLOR_QUADS_NO_DEPTH_WRITE, 0.78f, 0.48f, 0.02f, 0.92f);
        submitBeam(nodeCollector, poseStack, length,
                progress * 0.62f, POS_COLOR_QUADS_ADDITIVE, 1.0f, 0.72f, 0.18f, 0.92f);
    }

    private static void submitBeamCoreLayers(
            SubmitNodeCollector nodeCollector,
            PoseStack poseStack,
            float length,
            float progress
    ) {
        submitBeam(nodeCollector, poseStack, length,
                progress * 0.32f, POS_COLOR_QUADS_BLOOM_ADDITIVE, 1.0f, 1.0f, 1.0f, 1.0f);
        submitBeam(nodeCollector, poseStack, length,
                progress * 0.18f, POS_COLOR_QUADS_NO_DEPTH_WRITE, 1.0f, 1.0f, 1.0f, 0.98f);
    }

    private static void submitReflectionHighlight(
            SubmitNodeCollector nodeCollector,
            PoseStack poseStack,
            float reflectionDistance,
            float progress
    ) {
        if (progress <= 0.0f) return;
        poseStack.pushPose();
        poseStack.translate(0.0f, reflectionDistance, 0.0f);
        poseStack.translate(0.0f, -Math.min(0.12f, reflectionDistance), 0.0f);
        submitBeam(nodeCollector, poseStack, Math.min(0.24f, reflectionDistance),
                progress * 1.55f, POS_COLOR_QUADS_ADDITIVE, 1.0f, 0.92f, 0.58f, 0.95f);
        poseStack.popPose();
    }

    private static void submitBeam(
            SubmitNodeCollector nodeCollector,
            PoseStack poseStack,
            float length,
            float radius,
            RenderType renderType,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        if (radius <= 0.0f || length <= 0.0f) return;
        poseStack.pushPose();
        poseStack.scale(radius, length, radius);
        nodeCollector.submitCustomGeometry(
                poseStack,
                renderType,
                (pose, consumer) -> renderBeamLayer(
                        pose.pose(), consumer, red, green, blue, alpha
                )
        );
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
        reusedState.length = entity.getBeamLength();
        reusedState.reflectionActive = entity.isReflectionActive();
        reusedState.reflectionDistance = entity.getReflectionDistance();
    }

    @Override
    protected boolean affectedByCulling(RailgunRay display) {
        return false;
    }
}
