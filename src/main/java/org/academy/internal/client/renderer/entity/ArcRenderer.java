package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.academy.api.client.compatibility.IrisCompat;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.renderer.ArcFactory;
import org.academy.internal.client.renderer.entity.state.ArcRenderState;
import org.academy.internal.common.world.entity.skill.Arc;
import org.joml.Matrix4f;

public class ArcRenderer extends EntityRenderer<Arc, ArcRenderState> {
    public ArcRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ArcRenderState renderState, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (IrisCompat.isShadowRendererActive()) return;

        poseStack.pushPose();
        var matrix4f = new Matrix4f();
        matrix4f.rotateY((float) Math.toRadians(90 - renderState.yRot));
        matrix4f.rotateZ((float) Math.toRadians(90 + renderState.xRot));
        poseStack.mulPose(matrix4f);

        if (renderState.renderData != null) {
            ArcFactory.render(poseStack, BloomEffect.BUFFER_SOURCE, renderState.renderData);
        }

        poseStack.popPose();
    }

    @Override
    public void extractRenderState(Arc entity, ArcRenderState reusedState, float partialTick) {
        super.extractRenderState(entity, reusedState, partialTick);
        reusedState.renderData = entity.renderData;
        reusedState.yRot = entity.getYRot();
        reusedState.xRot = entity.getXRot();
    }

    @Override
    public ArcRenderState createRenderState() {
        return new ArcRenderState();
    }
}