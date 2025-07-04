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

    public EffectRenderEvent(@NotNull PoseStack newPoseStack, @NotNull MultiBufferSource newBuffer, int newPackedLight, @NotNull AbstractClientPlayer newLivingEntity, float newLimbSwing, float newLimbSwingAmount, float newPartialTick, float newAgeInTicks, float newNetHeadYaw, float newHeadPitch) {
        poseStack = newPoseStack;
        buffer = newBuffer;
        packedLight = newPackedLight;
        livingEntity = newLivingEntity;
        limbSwing = newLimbSwing;
        limbSwingAmount = newLimbSwingAmount;
        partialTick = newPartialTick;
        ageInTicks = newAgeInTicks;
        netHeadYaw = newNetHeadYaw;
        headPitch = newHeadPitch;
    }
}