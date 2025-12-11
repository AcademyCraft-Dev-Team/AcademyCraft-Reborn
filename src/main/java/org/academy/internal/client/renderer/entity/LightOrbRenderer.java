package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.academy.api.client.Render;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.renderer.BallRenderer;
import org.academy.api.client.util.VertexUtil;
import org.academy.internal.client.renderer.entity.state.LightOrbRenderState;
import org.academy.internal.common.world.entity.skill.LightOrb;
import org.joml.Matrix4f;

public class LightOrbRenderer extends EntityRenderer<LightOrb, LightOrbRenderState> {
    public static final float[][] HEAD_BUFFER = VertexUtil.Ball.getIcosphereVertexBuffer(1, 2, true);

    public LightOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void submit(LightOrbRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        float r = renderState.color.x();
        float g = renderState.color.y();
        float b = renderState.color.z();
        float scale = renderState.scale;

        var orientation = new Matrix4f()
                .rotateY((float) Math.toRadians(90 - renderState.yRot))
                .rotateZ((float) Math.toRadians(90 + renderState.xRot));

        poseStack.pushPose();
        poseStack.mulPose(orientation);

        poseStack.mulPose(new Matrix4f().scale(scale));

        BallRenderer.renderBall(
                poseStack.last(),
                BloomEffect.getBlitToMainPost().getBuffer(Render.RenderTypes.POS_COLOR_TRANGLES_BLOOM_POST),
                HEAD_BUFFER,
                r, g, b, 1.0f,
                15728880, // 满亮度
                OverlayTexture.NO_OVERLAY
        );

        poseStack.scale(0.85f, 0.85f, 0.85f);

        nodeCollector.submitCustomGeometry(
                poseStack,
                Render.RenderTypes.POS_COLOR_TRANGLES,
                (pose, vertexConsumer) -> BallRenderer.renderBall(
                        pose,
                        vertexConsumer,
                        HEAD_BUFFER,
                        1.0f, 1.0f, 1.0f, 1.0f,
                        15728880,
                        OverlayTexture.NO_OVERLAY
                )
        );

        poseStack.popPose();
    }

    @Override
    public LightOrbRenderState createRenderState() {
        return new LightOrbRenderState();
    }

    @Override
    public void extractRenderState(LightOrb entity, LightOrbRenderState reusedState, float partialTick) {
        super.extractRenderState(entity, reusedState, partialTick);
        reusedState.scale = entity.getScale();
        reusedState.color = entity.getColor();
        reusedState.xRot = entity.getXRot();
        reusedState.yRot = entity.getYRot();
    }

    @Override
    protected boolean affectedByCulling(LightOrb entity) {
        return false;
    }
}