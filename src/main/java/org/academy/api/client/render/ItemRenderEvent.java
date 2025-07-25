package org.academy.api.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class ItemRenderEvent extends Event implements ICancellableEvent {
    public ItemStack itemStack;
    public ItemDisplayContext displayContext;
    public boolean leftHand;
    public PoseStack poseStack;
    public MultiBufferSource buffer;
    public int combinedLight;
    public int combinedOverlay;
    public BakedModel model;
    public CallbackInfo ci;

    public ItemRenderEvent(ItemStack newItemStack, ItemDisplayContext newDisplayContext, boolean newLeftHand, PoseStack newPoseStack, MultiBufferSource newBuffer, int newCombinedLight, int newCombinedOverlay, BakedModel newModel, CallbackInfo newCi) {
        itemStack = newItemStack;
        displayContext = newDisplayContext;
        leftHand = newLeftHand;
        poseStack = newPoseStack;
        buffer = newBuffer;
        combinedLight = newCombinedLight;
        combinedOverlay = newCombinedOverlay;
        model = newModel;
        ci = newCi;
    }
}