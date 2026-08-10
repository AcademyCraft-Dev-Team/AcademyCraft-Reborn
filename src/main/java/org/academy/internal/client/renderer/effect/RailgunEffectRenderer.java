package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import org.academy.api.client.renderer.EffectRenderer;
import org.academy.internal.common.ability.electromaster.skills.lv5.Railgun;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.joml.Matrix4f;

import static org.academy.AcademyCraft.academy;
import static org.academy.api.client.render.Render.RenderTypes.ARC;
import static org.academy.internal.common.ability.electromaster.skills.lv5.Railgun.CHARGE_TIME;
import static org.academy.internal.common.ability.electromaster.skills.lv5.Railgun.RELEASE_VISUAL_TICKS;

public final class RailgunEffectRenderer implements EffectRenderer {
    public static final RailgunEffectRenderer INSTANCE = new RailgunEffectRenderer();
    public static final ContextKey<Railgun.Data> CONTEXT_KEY = new ContextKey<>(academy("railgun_data"));
    private static final int RING_SEGMENTS = 28;

    private RailgunEffectRenderer() {
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState renderState, float yRot, float xRot) {
        var data = renderState.getRenderData(CONTEXT_KEY);
        if (data == null) return;

        var ticks = data.ticks() + renderState.partialTick;
        var strength = getVisualStrength(data, ticks);
        if (strength <= 0.0f) return;

        var handX = data.rightHand() ? -0.32f : 0.32f;
        poseStack.pushPose();
        poseStack.translate(handX, 0.55f, -0.16f);
        submitHandRings(poseStack, collector, renderState.ageInTicks, strength, false);
        poseStack.popPose();

        if (data.coinReturnHint()) {
            var mainHandX = data.mainHandRight() ? -0.32f : 0.32f;
            poseStack.pushPose();
            poseStack.translate(mainHandX, 0.55f, -0.16f);
            submitCoinReturnHint(poseStack, collector, renderState.ageInTicks, false);
            poseStack.popPose();
        }
    }

    @Override
    public void renderFirstPerson(PoseStack poseStack, SubmitNodeCollector collector,
                                  LocalPlayer player, int packedLight, float partialTick) {
        var data = player.getExistingDataOrNull(AttachmentTypes.RAILGUN_DATA);
        if (data == null) return;

        var ticks = data.ticks() + partialTick;
        var strength = getVisualStrength(data, ticks);
        if (strength <= 0.0f) return;

        var handX = data.rightHand() ? 0.34f : -0.34f;
        poseStack.pushPose();
        poseStack.translate(handX, -0.20f, -0.30f);
        submitHandRings(poseStack, collector, player.tickCount + partialTick, strength, true);
        poseStack.popPose();

        if (data.coinReturnHint()) {
            var mainHandX = data.mainHandRight() ? 0.34f : -0.34f;
            poseStack.pushPose();
            poseStack.translate(mainHandX, -0.20f, -0.30f);
            submitCoinReturnHint(poseStack, collector, player.tickCount + partialTick, true);
            poseStack.popPose();
        }
    }

    private static float getVisualStrength(Railgun.Data data, float ticks) {
        if (data.released()) {
            return Mth.clamp(1.0f - ticks / RELEASE_VISUAL_TICKS, 0.0f, 1.0f);
        }
        if (CHARGE_TIME <= 0) return 1.0f;
        return Mth.clamp(ticks / CHARGE_TIME, 0.15f, 1.0f);
    }

    private static void submitHandRings(PoseStack poseStack, SubmitNodeCollector collector,
                                        float time, float strength, boolean firstPerson) {
        var scale = firstPerson ? 0.72f : 1.0f;
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        collector.submitCustomGeometry(poseStack, ARC, (pose, consumer) -> {
            var ringPose = new PoseStack();
            ringPose.last().set(pose);
            renderRingPlane(ringPose, consumer, time, 0, 0.30f, 0.026f, strength);

            ringPose.pushPose();
            ringPose.mulPose(Axis.XP.rotationDegrees(66.0f));
            ringPose.mulPose(Axis.ZP.rotationDegrees(time * 5.0f));
            renderRingPlane(ringPose, consumer, time, 1, 0.25f, 0.022f, strength * 0.88f);
            ringPose.popPose();

            ringPose.pushPose();
            ringPose.mulPose(Axis.YP.rotationDegrees(72.0f));
            ringPose.mulPose(Axis.ZP.rotationDegrees(-time * 6.5f));
            renderRingPlane(ringPose, consumer, time, 2, 0.21f, 0.019f, strength * 0.76f);
            ringPose.popPose();
        });
        poseStack.popPose();
    }

    private static void submitCoinReturnHint(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            float time,
            boolean firstPerson
    ) {
        var scale = firstPerson ? 0.72f : 1.0f;
        var pulse = 0.45f + 0.55f * Math.abs(Mth.sin(time * 0.85f));
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        collector.submitCustomGeometry(poseStack, ARC, (pose, consumer) -> {
            var ringPose = new PoseStack();
            ringPose.last().set(pose);
            ringPose.mulPose(Axis.ZP.rotationDegrees(-time * 9.0f));
            renderRingPlane(ringPose, consumer, time, 4, 0.36f, 0.034f, pulse);
        });
        poseStack.popPose();
    }

    private static void renderRingPlane(PoseStack poseStack, VertexConsumer consumer, float time,
                                        int ringIndex, float radius, float thickness, float alpha) {
        var matrix = poseStack.last().pose();
        var phase = time * (0.18f + ringIndex * 0.035f) + ringIndex * 2.1f;
        for (var segment = 0; segment < RING_SEGMENTS; segment++) {
            if ((segment + ringIndex * 3) % 11 == 0) continue;
            var angle0 = Mth.TWO_PI * segment / RING_SEGMENTS + phase;
            var angle1 = Mth.TWO_PI * (segment + 1) / RING_SEGMENTS + phase;
            var noise0 = Mth.sin(segment * 2.71f + time * 0.83f + ringIndex * 4.2f) * 0.018f;
            var noise1 = Mth.sin((segment + 1) * 2.71f + time * 0.83f + ringIndex * 4.2f) * 0.018f;
            var radius0 = radius + noise0;
            var radius1 = radius + noise1;
            var halfWidth0 = thickness * (0.75f + 0.25f * Mth.sin(time + segment));
            var halfWidth1 = thickness * (0.75f + 0.25f * Mth.sin(time + segment + 1));
            addRingVertex(consumer, matrix, angle0, radius0 - halfWidth0, 0.0f, 0.0f, alpha);
            addRingVertex(consumer, matrix, angle0, radius0 + halfWidth0, 0.0f, 1.0f, alpha);
            addRingVertex(consumer, matrix, angle1, radius1 + halfWidth1, 1.0f, 1.0f, alpha);
            addRingVertex(consumer, matrix, angle1, radius1 - halfWidth1, 1.0f, 0.0f, alpha);
        }
    }

    private static void addRingVertex(VertexConsumer consumer, Matrix4f matrix, float angle,
                                      float radius, float u, float v, float alpha) {
        var x = Mth.cos(angle) * radius;
        var y = Mth.sin(angle) * radius;
        var z = Mth.sin(angle * 3.0f) * 0.014f;
        consumer.addVertex(matrix, x, y, z)
                .setUv(u, v)
                .setColor(0.82f, 0.93f, 1.0f, alpha);
    }

}
