package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.compatibility.IrisCompat;
import org.academy.api.client.render.Render;
import org.academy.api.client.renderer.BoxRenderer;
import org.academy.internal.client.renderer.entity.state.HighSpeedElectronBeamRenderState;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.joml.Matrix4f;

public class HighSpeedElectronBeamRenderer extends EntityRenderer<HighSpeedElectronBeam, HighSpeedElectronBeamRenderState> {
    private static final AABB HEAD = new AABB(-1, -1, -1, 1, 1, 1);
    public static final AABB RAY = new AABB(-0.5, 0, -0.5, 0.5, 1, 0.5);
    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);

    public HighSpeedElectronBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void submit(HighSpeedElectronBeamRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        if (IrisCompat.isShadowRendererActive()) return;

        var ballRadius = renderState.progress * 0.185f;
        var logicalDirection = Vec3.directionFromRotation(renderState.xRot, renderState.yRot);
        var visualStart = Vec3.ZERO;
        if (Math.abs(renderState.visualSideOffset) > 1.0e-4f) {
            var horizontalForward = Vec3.directionFromRotation(0.0f, renderState.yRot);
            var right = horizontalForward.cross(WORLD_UP).normalize();
            visualStart = right.scale(renderState.visualSideOffset);
        }
        renderHead(poseStack, nodeCollector, visualStart, ballRadius);

        var rayVisualProgress = renderState.isCharging ? 0f : renderState.progress;
        var rayScale = rayVisualProgress * 0.25f * renderState.beamScale;
        var originalLength = ReflectedBeamVisualGeometry.safeLength(renderState.length);
        if (renderState.reflectionActive) {
            var reflectedLength = Math.clamp(renderState.reflectionDistance, 0.0f, originalLength);
            var reflectionPoint = logicalDirection.scale(reflectedLength);
            var returnEnd = ReflectedBeamVisualGeometry.fullReturnEnd(
                    reflectionPoint,
                    logicalDirection,
                    ReflectedBeamVisualGeometry.safeLength(renderState.reflectionReturnLength)
            );
            if (reflectedLength > 1.0e-6f) {
                renderBeamBetween(poseStack, nodeCollector, visualStart, reflectionPoint, rayScale, 1.0f);
            }
            renderBeamBetween(poseStack, nodeCollector, reflectionPoint, returnEnd, rayScale * 0.9f, 0.9f);
            renderHead(poseStack, nodeCollector, reflectionPoint, ballRadius * 0.8f);
        } else {
            var visualEnd = logicalDirection.scale(originalLength);
            renderBeamBetween(poseStack, nodeCollector, visualStart, visualEnd, rayScale, 1.0f);
        }
    }

    @Override
    public HighSpeedElectronBeamRenderState createRenderState() {
        return new HighSpeedElectronBeamRenderState();
    }

    @Override
    public void extractRenderState(HighSpeedElectronBeam entity, HighSpeedElectronBeamRenderState reusedState, float partialTick) {
        super.extractRenderState(entity, reusedState, partialTick);
        reusedState.length = entity.getBeamLength();
        reusedState.beamScale = entity.getBeamScale();
        reusedState.visualSideOffset = entity.getVisualSideOffset();
        reusedState.isCharging = entity.isCharging();
        reusedState.reflectionActive = entity.isReflectionActive();
        reusedState.reflectionDistance = entity.getReflectionDistance();
        reusedState.reflectionReturnLength = entity.getReflectionReturnLength();

        float progress;
        if (entity.isContinuous()) {
            progress = 1.0f;
            reusedState.isCharging = false;
        } else if (!entity.hasFired()) {
            reusedState.isCharging = true;
            progress = entity.isHeldCharge() && entity.getAttackDelayTicks() == 0
                    ? 1.0f
                    : (entity.currentChargerTicks + partialTick) / Math.max(1.0f, entity.getAttackDelayTicks());
        } else {
            reusedState.isCharging = false;
            progress = (entity.currentRayLifeTicks - partialTick) / HighSpeedElectronBeam.MAX_RAY_LIFE_TICKS;
        }
        reusedState.yRot = entity.getYRot();
        reusedState.xRot = entity.getXRot();
        reusedState.progress = Math.clamp(progress, 0.0f, 1.0f);
    }

    private static void renderHead(
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector,
            Vec3 position,
            float radius
    ) {
        if (radius <= 0.0f) return;
        poseStack.pushPose();
        poseStack.translate(position.x, position.y, position.z);
        poseStack.mulPose(new Matrix4f().scale(radius));
        nodeCollector.submitCustomGeometry(
                poseStack,
                Render.RenderTypes.POS_COLOR_QUADS_NO_DEPTH_WRITE,
                (pose, vertexConsumer) -> BoxRenderer.renderFilledBox(
                        pose, vertexConsumer, HEAD, 0, 1, 0, 0.125f
                )
        );
        poseStack.scale(0.5f, 0.5f, 0.5f);
        nodeCollector.submitCustomGeometry(
                poseStack,
                Render.RenderTypes.POS_COLOR_QUADS_NO_DEPTH_WRITE,
                (pose, vertexConsumer) -> BoxRenderer.renderFilledBox(
                        pose, vertexConsumer, HEAD, 1, 1, 1, 1
                )
        );
        poseStack.popPose();
    }

    private static void renderBeamBetween(
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector,
            Vec3 start,
            Vec3 end,
            float radius,
            float alphaScale
    ) {
        var direction = end.subtract(start);
        var length = direction.length();
        if (radius <= 0.0f || length <= 1.0e-6) return;
        var horizontalLength = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        var yaw = Math.toDegrees(Math.atan2(-direction.x, direction.z));
        var pitch = Math.toDegrees(Math.atan2(-direction.y, horizontalLength));

        poseStack.pushPose();
        poseStack.translate(start.x, start.y, start.z);
        poseStack.mulPose(new Matrix4f()
                .rotateY((float) Math.toRadians(90.0 - yaw))
                .rotateZ((float) Math.toRadians(90.0 + pitch))
        );
        renderRay(poseStack, nodeCollector, (float) length, radius,
                0, 1, 0, 0.125f * alphaScale);
        renderRay(poseStack, nodeCollector, (float) length, radius * 0.75f,
                1, 1, 1, alphaScale);
        poseStack.popPose();
    }

    private static void renderRay(
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector,
            float length,
            float radius,
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
                Render.RenderTypes.POS_COLOR_QUADS_NO_DEPTH_WRITE,
                (pose, vertexConsumer) -> BoxRenderer.renderFilledBox(
                        pose, vertexConsumer, RAY, red, green, blue, alpha
                )
        );
        poseStack.popPose();
    }

    @Override
    protected boolean affectedByCulling(HighSpeedElectronBeam display) {
        return false;
    }
}
