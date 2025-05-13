package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.renderer.EffectRenderer;
import org.academy.api.client.util.RenderUtil;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public class ArcEffectRenderer implements EffectRenderer {
    public static final EffectRenderer INSTANCE = new ArcEffectRenderer();
    public static final Map<String, Arc> ARCS = new HashMap<>();

    @Override
    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, @NotNull AbstractClientPlayer livingEntity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        poseStack.pushPose();
        for (Arc arc : ARCS.values()) {
            Vector3f fromOffset = arc.fromOffset;
            Vector3f to = arc.to;
            long seed = (long) (livingEntity.tickCount * to.x);
            RenderUtil.ArcRenderer.renderArc(poseStack, buffer, seed, fromOffset.x, fromOffset.y, fromOffset.z, to.x, to.y, to.z, arc.thickness, arc.segments);
        }
        poseStack.popPose();
    }

    public record Arc(Vector3f fromOffset, Vector3f to, float thickness, int segments) {
    }
}