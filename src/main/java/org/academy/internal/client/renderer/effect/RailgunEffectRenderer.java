package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemDisplayContext;
import org.academy.api.client.Render;
import org.academy.api.client.renderer.EffectRenderer;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.ability.electromaster.skills.lv5.Railgun;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static org.academy.AcademyCraft.academy;
import static org.academy.api.client.Render.RenderTypes.POS_COLOR_QUADS_BLOOM;
import static org.academy.internal.common.ability.electromaster.skills.lv5.Railgun.CHARGE_TIME;

public final class RailgunEffectRenderer implements EffectRenderer {
    public static final RailgunEffectRenderer INSTANCE = new RailgunEffectRenderer();
    public static final ContextKey<Railgun.Data> CONTEXT_KEY = new ContextKey<>(academy("railgun_data"));

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, AvatarRenderState renderState, float yRot, float xRot) {
        var data = renderState.getRenderData(CONTEXT_KEY);
        if (data == null) return;
        poseStack.pushPose();
        var xOffset = 0.35f;
        xOffset = data.rightHand() ? -xOffset : xOffset;
        var zOffset = 0.4f;
        var ticks = data.ticks() + renderState.partialTick;
        var yCurve = Math.max(0, -4.0f * ticks * (ticks - CHARGE_TIME) / (CHARGE_TIME * CHARGE_TIME));
        var chargeRatio = Math.clamp(ticks / (float) CHARGE_TIME, 0f, 1f);
        var coinX = xOffset;
        var coinY = -yCurve + 0.5f;
        var coinZ = -zOffset;
        poseStack.translate(coinX, coinY, coinZ);
        poseStack.mulPose(Axis.XP.rotationDegrees(renderState.ageInTicks * 50));
        var state = new ItemStackRenderState();
        Minecraft.getInstance()
                .getItemModelResolver()
                .updateForTopItem(
                        state,
                        Items.COIN.get().getDefaultInstance(),
                        ItemDisplayContext.FIXED,
                        null, null, 0
                );
        poseStack.scale(0.5f, 0.5f, 0.5f);
        state.submit(poseStack, submitNodeCollector, packedLight, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();

        // Glow and arc effects around the coin
        var capturedPose = poseStack;
        submitNodeCollector.submitCustomGeometry(poseStack, POS_COLOR_QUADS_BLOOM,
                (pose, vc) -> renderChargeEffects(capturedPose, vc, coinX, coinY, coinZ, chargeRatio, renderState.ageInTicks));
    }

    private void renderChargeEffects(PoseStack poseStack, VertexConsumer vc,
                                      float cx, float cy, float cz, float chargeRatio, float age) {
        if (chargeRatio <= 0.01f) return;

        var mat = poseStack.last().pose();

        // Glow disc (expanding billboarded ring around the coin)
        var glowAlpha = chargeRatio * 0.5f;
        var glowRadius = 0.2f + chargeRatio * 0.4f;
        var glowRings = 4;
        var glowSegments = 16;
        for (var ring = 0; ring < glowRings; ring++) {
            var r1 = glowRadius * ring / glowRings;
            var r2 = glowRadius * (ring + 1) / glowRings;
            var ringT = (float) ring / glowRings;
            var ringA = glowAlpha * (1f - ringT * 0.7f);

            for (var seg = 0; seg < glowSegments; seg++) {
                var a1 = (float) seg / glowSegments * (float) Math.PI * 2;
                var a2 = (float) (seg + 1) / glowSegments * (float) Math.PI * 2;
                var cos1 = (float) Math.cos(a1);
                var sin1 = (float) Math.sin(a1);
                var cos2 = (float) Math.cos(a2);
                var sin2 = (float) Math.sin(a2);

                // Gradient from white center to blue edge
                var ringR = 0.7f + ringT * 0.1f;
                var ringG = 0.8f + ringT * 0.1f;
                var ringB = 0.9f + ringT * 0.1f;

                vc.addVertex(mat, cx + cos1 * r1, cy + 0.01f, cz + sin1 * r1).setColor(ringR, ringG, ringB, ringA);
                vc.addVertex(mat, cx + cos2 * r1, cy + 0.01f, cz + sin2 * r1).setColor(ringR, ringG, ringB, ringA);
                vc.addVertex(mat, cx + cos2 * r2, cy + 0.01f, cz + sin2 * r2).setColor(ringR, ringG, ringB, ringA * 0.8f);
                vc.addVertex(mat, cx + cos1 * r2, cy + 0.01f, cz + sin1 * r2).setColor(ringR, ringG, ringB, ringA * 0.8f);
            }
        }

        // Electric arcs around the coin (zigzag lines in 3D)
        var arcCount = 3 + (int) (chargeRatio * 5);
        for (var a = 0; a < arcCount; a++) {
            var seed = a * 7919L + 12345;
            var arcVertAngle = (seed % 628) / 100f + age * 0.1f;
            var arcHorizAngle = (seed * 3 % 628) / 100f;
            var arcRadius = 0.15f + chargeRatio * 0.25f + hash(seed + 42) * 0.1f;

            float prevX = 0, prevY = 0, prevZ = 0;
            var prevValid = false;
            var arcSegs = 8;

            for (var s = 0; s <= arcSegs; s++) {
                var t = (float) s / arcSegs;
                var theta = arcHorizAngle + t * (float) Math.PI * 2.5f;
                var phi = arcVertAngle + t * (float) Math.PI * 0.8f;

                var x = cx + (float) Math.cos(theta) * (float) Math.cos(phi) * arcRadius;
                var y = cy + (float) Math.sin(phi) * arcRadius;
                var z = cz + (float) Math.sin(theta) * (float) Math.cos(phi) * arcRadius;

                // Add jitter
                x += hash(seed + s * 7) * 0.04f - 0.02f;
                y += hash(seed + s * 13 + 1) * 0.04f - 0.02f;
                z += hash(seed + s * 17 + 2) * 0.04f - 0.02f;

                if (prevValid) {
                    var arcAlpha = chargeRatio * 0.8f;
                    vc.addVertex(mat, prevX, prevY, prevZ).setColor(0.4f, 0.7f, 1f, arcAlpha);
                    vc.addVertex(mat, x, y, z).setColor(0.4f, 0.7f, 1f, arcAlpha);
                    vc.addVertex(mat, x + 0.005f, y + 0.005f, z).setColor(0.6f, 0.9f, 1f, arcAlpha * 0.5f);
                    vc.addVertex(mat, prevX + 0.005f, prevY + 0.005f, prevZ).setColor(0.6f, 0.9f, 1f, arcAlpha * 0.5f);
                }
                prevX = x;
                prevY = y;
                prevZ = z;
                prevValid = true;
            }
        }

        // Small spark particles
        var sparkCount = (int) (chargeRatio * 12);
        for (var i = 0; i < sparkCount; i++) {
            var seed = i * 8123L + 99991;
            var sparkAngle = hash(seed) * (float) Math.PI * 2;
            var sparkPhi = (hash(seed + 1) - 0.5f) * (float) Math.PI;
            var sparkDist = 0.12f + hash(seed + 2) * (0.3f + chargeRatio * 0.3f);
            var sparkSize = 0.01f + hash(seed + 3) * 0.03f;
            var sparkLife = (hash(seed + 4) * 0.5f + 0.5f);
            if (sparkLife < 0.3f) continue; // simulate birth/death cycling

            var sx = cx + (float) Math.cos(sparkAngle) * (float) Math.cos(sparkPhi) * sparkDist;
            var sy = cy + (float) Math.sin(sparkPhi) * sparkDist;
            var sz = cz + (float) Math.sin(sparkAngle) * (float) Math.cos(sparkPhi) * sparkDist;

            var sparkAlpha = chargeRatio * sparkLife * 0.6f;
            var sh = sparkSize;

            vc.addVertex(mat, sx - sh, sy, sz).setColor(0.7f, 0.9f, 1f, sparkAlpha);
            vc.addVertex(mat, sx + sh, sy, sz).setColor(0.7f, 0.9f, 1f, sparkAlpha);
            vc.addVertex(mat, sx, sy + sh * 2, sz).setColor(1f, 1f, 1f, sparkAlpha);
            vc.addVertex(mat, sx, sy, sz).setColor(0.7f, 0.9f, 1f, sparkAlpha);
        }
    }

    @Override
    public void renderFirstPerson(PoseStack poseStack, SubmitNodeCollector nodeCollector, LocalPlayer player, int packedLight, float partialTick) {
        if (!Railgun.Client.isCharging()) return;
        var data = player.getExistingDataOrNull(AttachmentTypes.RAILGUN_DATA);
        if (data == null) return;
        poseStack.pushPose();
        var xOffset = 0.35f;
        xOffset = data.rightHand() ? xOffset : -xOffset;
        var zOffset = 0.5f;
        var ticks = data.ticks() + partialTick;
        var yCurve = MathUtil.getParabolaHeight(CHARGE_TIME, 1, ticks);
        var chargeRatio = Math.clamp(ticks / (float) CHARGE_TIME, 0f, 1f);
        var coinX = xOffset;
        var coinY = yCurve * 0.5f - 0.125f;
        var coinZ = -zOffset;
        poseStack.translate(coinX, coinY, coinZ);

        poseStack.mulPose(Axis.XP.rotationDegrees((player.tickCount + partialTick) * 50));
        var state = new ItemStackRenderState();
        Minecraft.getInstance()
                .getItemModelResolver()
                .updateForTopItem(
                        state,
                        Items.COIN.get().getDefaultInstance(),
                        ItemDisplayContext.FIXED,
                        null, null, 0
                );
        poseStack.scale(0.125f, 0.125f, 0.125f);
        state.submit(poseStack, nodeCollector, packedLight, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();

        // Charge effects in first person
        var capturedPose = poseStack;
        nodeCollector.submitCustomGeometry(poseStack, POS_COLOR_QUADS_BLOOM,
                (pose, vc) -> renderChargeEffects(capturedPose, vc, coinX, coinY, coinZ, chargeRatio, player.tickCount + partialTick));
    }

    private static float hash(long seed) {
        var x = (seed ^ 0x9E3779B9L) * 0x9E3779B9L;
        return (float) ((x ^ (x >>> 16)) & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }
}
