package org.academy.internal.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.render.EffectRenderEvent;
import org.academy.api.client.renderer.RendererManager;

public class SkillEffectsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public SkillEffectsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, AvatarRenderState renderState, float yRot, float xRot) {
        RendererManager.renderEffect(poseStack, submitNodeCollector, packedLight, renderState, yRot, xRot);
        var event = new EffectRenderEvent(poseStack, submitNodeCollector, packedLight, renderState, yRot, xRot);
        NeoForge.EVENT_BUS.post(event);
    }
}