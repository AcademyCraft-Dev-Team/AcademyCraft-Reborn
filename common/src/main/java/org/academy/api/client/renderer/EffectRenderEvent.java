package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

public class EffectRenderEvent extends Event implements ICancellableEvent {
    public PoseStack poseStack;
    public MultiBufferSource buffer;
    public int packedLight;
    public AbstractClientPlayer livingEntity;
    public float limbSwing;
    public float limbSwingAmount;
    public float partialTick;
    public float ageInTicks;
    public float netHeadYaw;
    public float headPitch;

    public EffectRenderEvent(@NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, @NotNull AbstractClientPlayer livingEntity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        this.poseStack = poseStack;
        this.buffer = buffer;
        this.packedLight = packedLight;
        this.livingEntity = livingEntity;
        this.limbSwing = limbSwing;
        this.limbSwingAmount = limbSwingAmount;
        this.partialTick = partialTick;
        this.ageInTicks = ageInTicks;
        this.netHeadYaw = netHeadYaw;
        this.headPitch = headPitch;
    }
}