package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import org.academy.api.client.render.Render;
import org.academy.api.client.render.post.PostEffect;
import org.academy.internal.client.renderer.entity.state.GlowCircleRenderState;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.world.entity.skill.GlowCircle;

public class GlowCircleRenderer extends EntityRenderer<GlowCircle, GlowCircleRenderState> {
    private static final float MAX_RADIUS = 1.5f * 2.2f;

    public GlowCircleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static float radiusAt(float p) {
        return MAX_RADIUS * Mth.sin(p * Mth.PI);
    }

    @Override
    public void submit(GlowCircleRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        if (!isVisibleForCurrentCamera(renderState.ownerEntityId)) return;
        renderRing(poseStack, renderState.radius, renderState.xRot, renderState.yRot);
    }

    public static void renderRing(PoseStack poseStack, float radius, float xRot, float yRot) {
        var distortionStrength = 0.025f;
        var ringWidth = 0.5f;
        var ringEdgeBlur = 0.05f;

        poseStack.pushPose();

        var matrix = poseStack.last().pose();
        var halfRadius = radius / 2f;

        poseStack.mulPose(Axis.YP.rotationDegrees(90 - yRot));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90 + xRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(180));

        var vertexConsumer = PostEffect.getPre().getBuffer(Render.RenderTypes.DISTORTION_RING);
        vertexConsumer.addVertex(matrix, -halfRadius, 0, -halfRadius).setUv(0, 0).setNormal(distortionStrength, ringWidth, ringEdgeBlur);
        vertexConsumer.addVertex(matrix, halfRadius, 0, -halfRadius).setUv(1, 0).setNormal(distortionStrength, ringWidth, ringEdgeBlur);
        vertexConsumer.addVertex(matrix, halfRadius, 0, halfRadius).setUv(1, 1).setNormal(distortionStrength, ringWidth, ringEdgeBlur);
        vertexConsumer.addVertex(matrix, -halfRadius, 0, halfRadius).setUv(0, 1).setNormal(distortionStrength, ringWidth, ringEdgeBlur);

        poseStack.popPose();
    }

    @Override
    public GlowCircleRenderState createRenderState() {
        return new GlowCircleRenderState();
    }

    @Override
    public void extractRenderState(GlowCircle entity, GlowCircleRenderState reusedState, float partialTick) {
        super.extractRenderState(entity, reusedState, partialTick);
        var progress = Math.min((entity.ticks + partialTick) / GlowCircle.LIFE_TICKS, 1.0f);
        reusedState.radius = radiusAt(progress);
        reusedState.xRot = entity.getXRot();
        reusedState.yRot = entity.getYRot();
        reusedState.ownerEntityId = entity.getEffectOwnerId();
    }

    public static boolean isVisibleForCurrentCamera(int ownerEntityId) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null
                || !minecraft.options.getCameraType().isFirstPerson()
                || ownerEntityId != player.getId()) {
            return true;
        }
        return VectorReflection.Client.CONFIG.isFirstPersonDistortionRingVisible();
    }
}
