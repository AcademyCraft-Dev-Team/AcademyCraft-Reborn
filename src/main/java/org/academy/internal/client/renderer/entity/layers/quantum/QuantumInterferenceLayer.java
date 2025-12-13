package org.academy.internal.client.renderer.entity.layers.quantum;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.academy.AcademyCraft;
import org.academy.api.client.Render;

public class QuantumInterferenceLayer<S extends LivingEntityRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {
    public QuantumInterferenceLayer(RenderLayerParent<S, M> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, S renderState,float yRot, float partialTicks) {
        if (!(renderState instanceof QuantumRenderStateExtension ext) || !ext.academy$isQuantumActive()) {
            return;
        }

        var intensity = ext.academy$getQuantumIntensity();
        if (intensity <= 0.001f) return;

        var color = ext.academy$getQuantumColor();
        var r = ((color >> 16) & 0xFF) / 255.0f;
        var g = ((color >> 8) & 0xFF) / 255.0f;
        var b = (color & 0xFF) / 255.0f;

        var renderType = Render.RenderTypes.POS_COLOR_QUADS_BLOOM_POST;

        var time = renderState.ageInTicks + partialTicks;
        var model = getParentModel();

        nodeCollector.submitCustomGeometry(
                poseStack,
                renderType,
                (pose, vertexConsumer) -> {
                    try {
                        var rootPart = model.root();

                        var childrenMap = rootPart.children;

                        if (childrenMap.isEmpty()) return;

                        var parts = childrenMap.values();

                        var tempStack = new PoseStack();
                        tempStack.last().pose().set(pose.pose());

                        var partIndex = 0;

                        for (var part : parts) {
                            partIndex++;

                            if (!part.visible) continue;
                            var wave = Math.sin(time * 0.08f + partIndex * 132.0f);

                            if (wave > 0.2) {
                                var localAlpha = (float) ((wave - 0.2) / 0.8);
                                localAlpha *= (intensity + 0.5f);
                                localAlpha *= 0.65f;

                                renderPartGlitch(part, tempStack, vertexConsumer, 1.05f, 0.02f, r, g, b, localAlpha);

                                if (localAlpha > 0.3f) {
                                    renderPartGlitch(part, tempStack, vertexConsumer, 1.08f, -0.01f, r, g, b, localAlpha * 0.5f);
                                }
                            }
                        }
                    } catch (Exception e) {
                        AcademyCraft.LOGGER.warn("Failed to render Quantum FX for model: {}", model.getClass().getName());
                    }
                }
        );
    }

    private void renderPartGlitch(ModelPart part, PoseStack poseStack, VertexConsumer buffer, float scale, float offset, float r, float g, float b, float a) {
        poseStack.pushPose();

        poseStack.translate(offset, offset, 0);
        poseStack.scale(scale, scale, scale);

        var argb = (int) (a * 255) << 24 | (int) (r * 255) << 16 | (int) (g * 255) << 8 | (int) (b * 255);
        part.render(poseStack, buffer, 15728880, 0, argb);

        poseStack.popPose();
    }
}