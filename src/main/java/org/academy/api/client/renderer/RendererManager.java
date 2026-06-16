package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import java.util.LinkedHashSet;
import java.util.Set;

public final class RendererManager {
    private static final Set<EffectRenderer> EFFECT_RENDERERS = new LinkedHashSet<>();

    public static void init() {
    }

    public static void registerEffectRenderer(EffectRenderer renderer) {
        EFFECT_RENDERERS.add(renderer);
    }

    public static void renderEffect(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, AvatarRenderState renderState, float yRot, float xRot) {
        for (var renderer : EFFECT_RENDERERS) {
            renderer.render(poseStack, submitNodeCollector, packedLight, renderState, yRot, xRot);
        }
    }

    public static void renderEffectFirstPerson(PoseStack poseStack, SubmitNodeCollector nodeCollector, LocalPlayer player, int packedLight, float partialTick) {
        for (var renderer : EFFECT_RENDERERS) {
            renderer.renderFirstPerson(poseStack, nodeCollector, player, packedLight, partialTick);
        }
    }
}