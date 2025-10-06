package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

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

    public static void renderEffect(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, AvatarRenderState renderState, float yRot, float xRot) {
        for (var renderer : EFFECT_RENDERERS) {
            renderer.render(poseStack, submitNodeCollector, packedLight, renderState, yRot, xRot);
        }
    }
}