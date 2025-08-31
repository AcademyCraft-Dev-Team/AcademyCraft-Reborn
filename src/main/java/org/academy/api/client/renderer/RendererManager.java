package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;

import java.util.ArrayList;
import java.util.List;

public final class RendererManager {
    private static boolean initialized = false;
    private static final List<EffectRenderer> EFFECT_RENDERERS = new ArrayList<>();

    public static void init() {
        initialized = true;
    }

    public static void registerEffectRenderer(EffectRenderer renderer) {
        if (!initialized) {
            EFFECT_RENDERERS.add(renderer);
        }
    }

    public static void renderEffect(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, PlayerRenderState renderState, float yRot, float xRot) {
        for (var renderer : EFFECT_RENDERERS) {
            renderer.render(poseStack, bufferSource, packedLight, renderState, yRot, xRot);
        }
    }
}