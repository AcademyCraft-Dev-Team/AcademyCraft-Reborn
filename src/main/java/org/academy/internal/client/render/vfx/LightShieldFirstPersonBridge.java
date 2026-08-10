package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.academy.api.client.renderer.EffectRenderer;
import org.academy.api.client.resources.R;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.joml.Matrix4f;

public final class LightShieldFirstPersonBridge implements EffectRenderer {
    public static final LightShieldFirstPersonBridge INSTANCE = new LightShieldFirstPersonBridge();
    private static final float FIRST_PERSON_HALF_SIZE = 2.25f;

    private LightShieldFirstPersonBridge() {
    }

    private static void submitShield(PoseStack poseStack, SubmitNodeCollector collector,
                                     int packedLight, float halfSize) {
        collector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityTranslucent(R.textures.light_shield_effect),
                (pose, consumer) -> addDoubleSidedQuad(consumer, pose.pose(), packedLight, halfSize)
        );
    }

    private static void addDoubleSidedQuad(VertexConsumer consumer, Matrix4f matrix,
                                           int packedLight, float halfSize) {
        addQuad(consumer, matrix, packedLight, halfSize, false);
        addQuad(consumer, matrix, packedLight, halfSize, true);
    }

    private static void addQuad(VertexConsumer consumer, Matrix4f matrix, int packedLight,
                                float halfSize, boolean reverse) {
        if (reverse) {
            vertex(consumer, matrix, halfSize, -halfSize, 1, 0, packedLight);
            vertex(consumer, matrix, -halfSize, -halfSize, 0, 0, packedLight);
            vertex(consumer, matrix, -halfSize, halfSize, 0, 1, packedLight);
            vertex(consumer, matrix, halfSize, halfSize, 1, 1, packedLight);
        } else {
            vertex(consumer, matrix, -halfSize, -halfSize, 0, 0, packedLight);
            vertex(consumer, matrix, halfSize, -halfSize, 1, 0, packedLight);
            vertex(consumer, matrix, halfSize, halfSize, 1, 1, packedLight);
            vertex(consumer, matrix, -halfSize, halfSize, 0, 1, packedLight);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y,
                               float u, float v, int packedLight) {
        consumer.addVertex(matrix, x, y, 0)
                .setColor(1.0f, 1.0f, 1.0f, 0.85f)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(0, 0, 1);
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState renderState, float yRot, float xRot) {
        // Third-person light shield is rendered by LightShieldVfx through the Vfx pipeline.
    }

    @Override
    public void renderFirstPerson(PoseStack poseStack, SubmitNodeCollector collector,
                                  LocalPlayer player, int packedLight, float partialTick) {
        if (!player.getData(AttachmentTypes.LIGHT_SHIELD_ACTIVE.get())) return;
        poseStack.pushPose();
        poseStack.translate(0, 0, -1.5);
        poseStack.mulPose(Axis.ZP.rotationDegrees((player.tickCount + partialTick) * 12.0f));
        submitShield(poseStack, collector, packedLight, FIRST_PERSON_HALF_SIZE);
        poseStack.popPose();
    }
}
