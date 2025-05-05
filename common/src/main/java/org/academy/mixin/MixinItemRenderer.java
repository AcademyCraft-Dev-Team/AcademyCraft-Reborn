package org.academy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.academy.AcademyCraft;
import org.academy.api.client.renderer.ItemRenderEvent;
import org.academy.api.client.renderer.RendererManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public class MixinItemRenderer {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void render(ItemStack itemStack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay, BakedModel model, CallbackInfo ci) {
        ItemRenderEvent event = new ItemRenderEvent(itemStack, displayContext, leftHand, poseStack, buffer, combinedLight, combinedOverlay, model, ci);
        AcademyCraft.EVENT_BUS.post(event);
        if (event.isCanceled()) return;
        itemStack = event.itemStack;
        displayContext = event.displayContext;
        leftHand = event.leftHand;
        poseStack = event.poseStack;
        buffer = event.buffer;
        combinedLight = event.combinedLight;
        combinedOverlay = event.combinedOverlay;
        model = event.model;
        final Item item = itemStack.getItem();
        if (RendererManager.ITEM_RENDERER_MAP.containsKey(item)) {
            RendererManager.ITEM_RENDERER_MAP.get(item).render(itemStack, displayContext, leftHand, poseStack, buffer, combinedLight, combinedOverlay, model);
            ci.cancel();
        }
    }
}