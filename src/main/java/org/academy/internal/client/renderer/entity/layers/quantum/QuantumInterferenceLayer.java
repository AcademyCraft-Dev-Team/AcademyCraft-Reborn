package org.academy.internal.client.renderer.entity.layers.quantum;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import org.academy.api.client.Render;
import org.academy.api.client.render.post.BloomEffect;

import static org.academy.AcademyCraft.academy;

public class QuantumInterferenceLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends RenderLayer<S, M> {
    public static final ContextKey<QuantumData> CONTEXT_KEY = new ContextKey<>(academy("quantum"));

    public QuantumInterferenceLayer(RenderLayerParent<S, M> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, S renderState, float yRot, float partialTicks) {
        var quantum = renderState.getRenderData(CONTEXT_KEY);
        if (quantum == null || !quantum.active()) return;

        var intensity = quantum.intensity();

        var color = quantum.color();
        var r = ((color >> 16) & 0xFF) / 255.0f;
        var g = ((color >> 8) & 0xFF) / 255.0f;
        var b = (color & 0xFF) / 255.0f;

        var renderType = Render.RenderTypes.POS_COLOR_QUADS_BLOOM_POST;
        var vertexConsumer = BloomEffect.getAfter().getBuffer(renderType);

        var model = getParentModel();
        model.renderToBuffer(
                poseStack,
                vertexConsumer,
                LightCoordsUtil.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                ARGB.colorFromFloat(
                        intensity * Math.abs(Mth.sin(renderState.ageInTicks * 0.1)),
                        r, g, b
                )
        );
    }
}