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
        var visualYaw = renderState.yRot;
        var visualPitch = renderState.xRot;
        var visualLength = renderState.length;
        var visualStart = Vec3.ZERO;
        if (Math.abs(renderState.visualSideOffset) > 1.0e-4f) {
            var logicalDirection = Vec3.directionFromRotation(renderState.xRot, renderState.yRot);
            var horizontalForward = Vec3.directionFromRotation(0.0f, renderState.yRot);
            var right = horizontalForward.cross(WORLD_UP).normalize();
            visualStart = right.scale(renderState.visualSideOffset);
            var visualDirection = logicalDirection.scale(renderState.length).subtract(visualStart);
            var horizontalLength = Math.sqrt(visualDirection.x * visualDirection.x
                    + visualDirection.z * visualDirection.z);
            visualYaw = (float) Math.toDegrees(Math.atan2(-visualDirection.x, visualDirection.z));
            visualPitch = (float) Math.toDegrees(Math.atan2(-visualDirection.y, horizontalLength));
            visualLength = (float) visualDirection.length();
        }

        var commonInitialOrientation = new Matrix4f()
                .rotateY((float) Math.toRadians(90 - visualYaw))
                .rotateZ((float) Math.toRadians(90 + visualPitch));

        poseStack.pushPose();
        poseStack.translate(visualStart.x, visualStart.y, visualStart.z);
        poseStack.mulPose(commonInitialOrientation);

        poseStack.pushPose();
        poseStack.mulPose(new Matrix4f().scale(ballRadius));
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
                Render.RenderTypes.POS_COLOR_QUADS,
                (pose, vertexConsumer) -> BoxRenderer.renderFilledBox(
                        pose, vertexConsumer, HEAD, 1, 1, 1, 1
                )
        );
        poseStack.popPose();

        var rayVisualProgress = renderState.isCharging ? 0f : renderState.progress;

        poseStack.pushPose();
        var rayScale = rayVisualProgress * 0.25f * renderState.beamScale;
        poseStack.mulPose(new Matrix4f().scale(rayScale, visualLength, rayScale));
        nodeCollector.submitCustomGeometry(
                poseStack,
                Render.RenderTypes.POS_COLOR_QUADS_NO_DEPTH_WRITE,
                (pose, vertexConsumer) -> BoxRenderer.renderFilledBox(
                        pose, vertexConsumer, RAY, 0, 1, 0, 0.125f
                )
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
        reusedState.length = entity.getBeamLength();
        reusedState.beamScale = entity.getBeamScale();
        reusedState.visualSideOffset = entity.getVisualSideOffset();
        reusedState.isCharging = entity.isCharging();

        float progress;
        if (entity.isContinuous()) {
            progress = 1.0f;
            reusedState.isCharging = false;
        } else if (entity.isCharging()) {
            progress = (entity.currentChargerTicks + partialTick) / HighSpeedElectronBeam.MAX_CHARGE_TICKS;
        } else {
            progress = (entity.currentRayLifeTicks - partialTick) / HighSpeedElectronBeam.MAX_RAY_LIFE_TICKS;
        }
        reusedState.yRot = entity.getYRot();
        reusedState.xRot = entity.getXRot();
        reusedState.progress = Math.clamp(progress, 0.0f, 1.0f);
    }

    @Override
    protected boolean affectedByCulling(HighSpeedElectronBeam display) {
        return false;
    }
}
