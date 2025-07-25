package org.academy.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.render.ItemRenderEvent;
import org.academy.api.client.renderer.RendererManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class MixinItemRenderer {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void render(ItemStack itemStack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay, BakedModel model, CallbackInfo ci) {
        var event = new ItemRenderEvent(itemStack, displayContext, leftHand, poseStack, buffer, combinedLight, combinedOverlay, model, ci);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return;
        itemStack = event.itemStack;
        displayContext = event.displayContext;
        leftHand = event.leftHand;
        poseStack = event.poseStack;
        buffer = event.buffer;
        combinedLight = event.combinedLight;
        combinedOverlay = event.combinedOverlay;
        model = event.model;
        if (RendererManager.handleItemRender(itemStack, displayContext, leftHand, poseStack, buffer, combinedLight, combinedOverlay, model)) {
            ci.cancel();
        }
    }
}