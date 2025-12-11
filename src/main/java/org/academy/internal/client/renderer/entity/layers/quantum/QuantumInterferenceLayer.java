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

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

public class QuantumInterferenceLayer<S extends LivingEntityRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {

    private static Field childrenField;

    static {
        try {
            childrenField = ModelPart.class.getDeclaredField("children");
            childrenField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to access ModelPart.children field via reflection", e);
        }
    }

    public QuantumInterferenceLayer(RenderLayerParent<S, M> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, S renderState,float yRot, float partialTicks) {
        if (!(renderState instanceof QuantumRenderStateExtension ext) || !ext.academy$isQuantumActive()) {
            return;
        }

        float intensity = ext.academy$getQuantumIntensity();
        if (intensity <= 0.001f) return;

        int color = ext.academy$getQuantumColor();
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        var renderType = Render.RenderTypes.POS_COLOR_QUADS_BLOOM_POST;

        float time = renderState.ageInTicks + partialTicks;
        M model = this.getParentModel();

        nodeCollector.submitCustomGeometry(
                poseStack,
                renderType,
                (pose, vertexConsumer) -> {
                    try {
                        ModelPart rootPart = model.root();
                        if (rootPart == null) return;

                        @SuppressWarnings("unchecked")
                        Map<String, ModelPart> childrenMap = (Map<String, ModelPart>) childrenField.get(rootPart);

                        if (childrenMap == null || childrenMap.isEmpty()) return;

                        Collection<ModelPart> parts = childrenMap.values();

                        PoseStack tempStack = new PoseStack();
                        tempStack.last().pose().set(pose.pose());

                        int partIndex = 0;

                        for (ModelPart part : parts) {
                            partIndex++;

                            if (!part.visible) continue;
                            double wave = Math.sin(time * 0.08f + partIndex * 132.0f);

                            if (wave > 0.2) {
                                float localAlpha = (float) ((wave - 0.2) / 0.8);
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

        int argb = (int)(a * 255) << 24 | (int)(r * 255) << 16 | (int)(g * 255) << 8 | (int)(b * 255);
        part.render(poseStack, buffer, 15728880, 0, argb);

        poseStack.popPose();
    }
}