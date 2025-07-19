package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.renderer.ArcRenderer;
import org.academy.api.client.renderer.EffectRenderer;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public final class ArcEffectRenderer implements EffectRenderer {
    public static final EffectRenderer INSTANCE = new ArcEffectRenderer();
    public static final Map<String, Arc> ARCS = new HashMap<>();

    @Override
    public void render(@NotNull PoseStack newPoseStack, @NotNull MultiBufferSource newBuffer, int newPackedLight, @NotNull AbstractClientPlayer newLivingEntity, float newLimbSwing, float newLimbSwingAmount, float newPartialTick, float newAgeInTicks, float newNetHeadYaw, float newHeadPitch) {
        newPoseStack.pushPose();
        for (var arc : ARCS.values()) {
            var fromOffset = arc.fromOffset;
            var to = arc.to;
            var seed = (long) (newLivingEntity.tickCount * to.x);
            ArcRenderer.renderArc(newPoseStack, newBuffer, seed, fromOffset.x, fromOffset.y, fromOffset.z, to.x, to.y, to.z, arc.thickness, arc.segments);
        }
        newPoseStack.popPose();
    }

    public record Arc(Vector3f fromOffset, Vector3f to, float thickness, int segments) {
    }
}