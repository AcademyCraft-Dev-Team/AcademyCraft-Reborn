package org.academy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import org.academy.api.client.render.AcademyCraftRenderSystem;
import org.academy.internal.client.renderer.effect.StormWingEffectRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = {"renderLevel"}, at = {@At(value = "CONSTANT", args = {"stringValue=entities"}, ordinal = 0)})
    private void afterEntities(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        for (AcademyCraftRenderSystem.Renderer renderer : AcademyCraftRenderSystem.RENDERER_MAP.values()) {
            renderer.render(poseStack, partialTick, finishNanoTime, renderBlockOutline, camera, gameRenderer, lightTexture, projectionMatrix, ci);
        }
    }
}