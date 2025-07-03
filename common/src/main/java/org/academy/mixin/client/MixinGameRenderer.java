package org.academy.mixin.client;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.academy.api.client.hud.HUDManager;
import org.academy.internal.client.renderer.Shaders;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Map;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    @Shadow
    private final Map<String, ShaderInstance> shaders = Maps.newHashMap();

    @Inject(method = "reloadShaders", at = @At("TAIL"))
    private void reloadShaders(ResourceProvider resourceProvider, CallbackInfo ci) {
        for (var shader : Shaders.SHADERS) {
            shaders.put(shader.toString(), shader.apply(resourceProvider));
        }
    }

    @Inject(method = "render", locals = LocalCapture.CAPTURE_FAILSOFT, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;F)V"))
    public void gui(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci, int i, int j, Window window, Matrix4f matrix4f, PoseStack posestack, GuiGraphics guigraphics) {
        HUDManager.render(guigraphics, partialTicks);
    }
}