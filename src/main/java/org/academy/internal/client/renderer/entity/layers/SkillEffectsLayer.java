package org.academy.internal.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.render.EffectRenderEvent;
import org.academy.api.client.renderer.RendererManager;

public class SkillEffectsLayer extends RenderLayer<PlayerRenderState, PlayerModel> {
    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, PlayerRenderState renderState, float yRot, float xRot) {
        RendererManager.renderEffect(poseStack, bufferSource, packedLight, renderState, yRot, xRot);
        var event = new EffectRenderEvent(poseStack, bufferSource, packedLight, renderState, yRot, xRot);
        NeoForge.EVENT_BUS.post(event);
    }

    public SkillEffectsLayer(RenderLayerParent<PlayerRenderState, PlayerModel> renderer) {
        super(renderer);
    }
}