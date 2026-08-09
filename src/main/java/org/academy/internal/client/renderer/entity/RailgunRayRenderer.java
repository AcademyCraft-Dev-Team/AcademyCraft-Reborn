package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.renderer.CylinderRenderer;
import org.academy.api.client.util.VertexUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.client.renderer.entity.state.RailgunRayRenderState;
import org.academy.internal.common.world.entity.skill.RailgunRay;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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

        var widthMultiplier = Float.isFinite(renderState.widthMultiplier)
                ? Math.max(0.0f, renderState.widthMultiplier)
                : 1.0f;
        var progress = Math.max(0.0f,
                MathUtil.getFlatTopParabolaHeight(renderState.ageInTicks, 20, 5))
                * 0.1f * widthMultiplier;
        var originalLength = ReflectedBeamVisualGeometry.safeLength(renderState.length);
        var outgoingLength = renderState.reflectionActive
                ? Math.min(originalLength, ReflectedBeamVisualGeometry.safeLength(renderState.reflectionDistance))
                : originalLength;
        var outgoingDirection = Vec3.directionFromRotation(renderState.xRot, renderState.yRot).normalize();
        submitBeamShellLayersAlong(
                nodeCollector, poseStack, Vec3.ZERO, outgoingDirection, outgoingLength, progress
        );

        if (renderState.reflectionActive) {
            var reflectionPoint = outgoingDirection.scale(outgoingLength);
            submitBeamShellLayersAlong(
                    nodeCollector,
                    poseStack,
                    reflectionPoint,
                    renderState.reflectionReturnDirection,
                    ReflectedBeamVisualGeometry.safeLength(renderState.reflectionReturnLength),
                    progress * 1.08f
            );
        }

        submitBeamCoreLayersAlong(
                nodeCollector, poseStack, Vec3.ZERO, outgoingDirection, outgoingLength, progress
        );

        if (renderState.reflectionActive) {
            var reflectionPoint = outgoingDirection.scale(outgoingLength);
            submitBeamCoreLayersAlong(
                    nodeCollector,
                    poseStack,
                    reflectionPoint,
                    renderState.reflectionReturnDirection,
                    ReflectedBeamVisualGeometry.safeLength(renderState.reflectionReturnLength),
                    progress * 1.08f
            );
            submitReflectionHighlight(
                    nodeCollector, poseStack, reflectionPoint, outgoingDirection, outgoingLength, progress
            );
        }
        poseStack.popPose();
    }

    private static void submitBeamShellLayersAlong(
            SubmitNodeCollector nodeCollector,
            PoseStack poseStack,
            Vec3 start,
            Vec3 direction,
            float length,
            float progress
    ) {
        if (!prepareSegmentPose(poseStack, start, direction, length)) return;
        submitBeamShellLayers(nodeCollector, poseStack, length, progress);
        poseStack.popPose();
    }

    private static void submitBeamCoreLayersAlong(
            SubmitNodeCollector nodeCollector,
            PoseStack poseStack,
            Vec3 start,
            Vec3 direction,
            float length,
            float progress
    ) {
        if (!prepareSegmentPose(poseStack, start, direction, length)) return;
        submitBeamCoreLayers(nodeCollector, poseStack, length, progress);
        poseStack.popPose();
    }

    private static boolean prepareSegmentPose(
            PoseStack poseStack,
            Vec3 start,
            Vec3 direction,
            float length
    ) {
        var directionLengthSqr = direction == null ? 0.0 : direction.lengthSqr();
        if (!(length > 0.0f)
                || !Float.isFinite(length)
                || !Double.isFinite(directionLengthSqr)
                || directionLengthSqr <= 1.0e-12) {
            return false;
        }
        var normalized = direction.normalize();
        poseStack.pushPose();
        poseStack.translate(start.x, start.y, start.z);
        poseStack.mulPose(new Quaternionf().rotationTo(
                new Vector3f(0.0f, 1.0f, 0.0f),
                new Vector3f((float) normalized.x, (float) normalized.y, (float) normalized.z)
        ));
        return true;
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
            Vec3 reflectionPoint,
            Vec3 outgoingDirection,
            float reflectionDistance,
            float progress
    ) {
        if (progress <= 0.0f) return;
        var highlightLength = Math.min(0.24f, reflectionDistance);
        var start = reflectionPoint.subtract(outgoingDirection.scale(Math.min(0.12f, reflectionDistance)));
        if (!prepareSegmentPose(poseStack, start, outgoingDirection, highlightLength)) return;
        submitBeam(
                nodeCollector, poseStack, highlightLength, progress * 1.55f,
                POS_COLOR_QUADS_ADDITIVE, 1.0f, 0.92f, 0.58f, 0.95f
        );
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
        reusedState.widthMultiplier = entity.getBeamWidthMultiplier();
        reusedState.reflectionActive = entity.isReflectionActive();
        reusedState.reflectionDistance = entity.getReflectionDistance();
        reusedState.reflectionReturnLength = entity.getReflectionReturnLength();
        reusedState.reflectionReturnDirection = entity.getReflectionReturnDirection();
    }

    @Override
    protected boolean affectedByCulling(RailgunRay display) {
        return false;
    }
}
