package org.academy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.academy.AbilitySystemClient;
import org.academy.api.client.render.RenderSystem;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = {"renderLevel"}, at = {@At(value = "CONSTANT", args = {"stringValue=entities"}, ordinal = 0)})
    private void afterEntities(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo ci) {
        for (AbilitySystemClient.Renderer renderer : RenderSystem.RENDERER_LIST) {
            renderer.render(poseStack, f, l, bl, camera, gameRenderer, lightTexture, matrix4f, ci);
        }
    }
}