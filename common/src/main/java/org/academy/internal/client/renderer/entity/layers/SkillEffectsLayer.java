package org.academy.internal.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.academy.AcademyCraft;
import org.academy.api.client.renderer.EffectRenderEvent;
import org.academy.api.client.renderer.EffectRenderer;
import org.academy.api.client.renderer.RendererManager;
import org.jetbrains.annotations.NotNull;

public class SkillEffectsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static SkillEffectsLayer INSTANCE;

    public SkillEffectsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
    }

    @Override
    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, @NotNull AbstractClientPlayer livingEntity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        EffectRenderEvent event = new EffectRenderEvent(poseStack, buffer, packedLight, livingEntity, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
        AcademyCraft.EVENT_BUS.post(event);
        if (event.isCanceled()) return;
        poseStack = event.poseStack;
        buffer = event.buffer;
        packedLight = event.packedLight;
        livingEntity = event.livingEntity;
        limbSwing = event.limbSwing;
        limbSwingAmount = event.limbSwingAmount;
        partialTick = event.partialTick;
        ageInTicks = event.ageInTicks;
        netHeadYaw = event.netHeadYaw;
        headPitch = event.headPitch;
        for (EffectRenderer effectRenderer : RendererManager.EFFECT_RENDERER_MAP) {
            effectRenderer.render(poseStack, buffer, packedLight, livingEntity, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
        }
    }
}