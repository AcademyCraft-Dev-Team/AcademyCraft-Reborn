package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.context.ContextKey;
import org.academy.api.client.renderer.EffectRenderer;
import org.academy.api.client.resources.R;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.joml.Matrix4f;

import static org.academy.AcademyCraft.academy;

public final class LightShieldEffectRenderer implements EffectRenderer {
    public static final ContextKey<Boolean> CONTEXT_KEY = new ContextKey<>(academy("light_shield"));
    public static final LightShieldEffectRenderer INSTANCE = new LightShieldEffectRenderer();
    private static final float HALF_SIZE = 2.25f;

    private LightShieldEffectRenderer() {
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        if (!state.getRenderDataOrDefault(CONTEXT_KEY, false)) return;
        poseStack.pushPose();
        poseStack.translate(0, -1.05, -1.5);
        poseStack.mulPose(Axis.XP.rotationDegrees(-xRot));
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.ageInTicks * 12.0f));
        submitShield(poseStack, collector, packedLight);
        poseStack.popPose();
    }

    @Override
    public void renderFirstPerson(PoseStack poseStack, SubmitNodeCollector collector,
                                  LocalPlayer player, int packedLight, float partialTick) {
        if (!player.getData(AttachmentTypes.LIGHT_SHIELD_ACTIVE.get())) return;
        poseStack.pushPose();
        poseStack.translate(0, 0, -1.5);
        poseStack.mulPose(Axis.ZP.rotationDegrees((player.tickCount + partialTick) * 12.0f));
        submitShield(poseStack, collector, packedLight);
        poseStack.popPose();
    }

    private static void submitShield(PoseStack poseStack, SubmitNodeCollector collector, int packedLight) {
        collector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityTranslucent(R.textures.light_shield_effect),
                (pose, consumer) -> addDoubleSidedQuad(consumer, pose.pose(), packedLight)
        );
    }

    private static void addDoubleSidedQuad(VertexConsumer consumer, Matrix4f matrix, int packedLight) {
        addQuad(consumer, matrix, packedLight, false);
        addQuad(consumer, matrix, packedLight, true);
    }

    private static void addQuad(VertexConsumer consumer, Matrix4f matrix, int packedLight, boolean reverse) {
        if (reverse) {
            vertex(consumer, matrix, HALF_SIZE, -HALF_SIZE, 1, 0, packedLight);
            vertex(consumer, matrix, -HALF_SIZE, -HALF_SIZE, 0, 0, packedLight);
            vertex(consumer, matrix, -HALF_SIZE, HALF_SIZE, 0, 1, packedLight);
            vertex(consumer, matrix, HALF_SIZE, HALF_SIZE, 1, 1, packedLight);
        } else {
            vertex(consumer, matrix, -HALF_SIZE, -HALF_SIZE, 0, 0, packedLight);
            vertex(consumer, matrix, HALF_SIZE, -HALF_SIZE, 1, 0, packedLight);
            vertex(consumer, matrix, HALF_SIZE, HALF_SIZE, 1, 1, packedLight);
            vertex(consumer, matrix, -HALF_SIZE, HALF_SIZE, 0, 1, packedLight);
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
}
