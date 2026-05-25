package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import org.academy.api.client.Render;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.renderer.BallRenderer;
import org.academy.api.client.util.VertexUtil;
import org.academy.internal.client.renderer.entity.state.LightOrbRenderState;
import org.academy.internal.common.world.entity.skill.LightOrb;

public class LightOrbRenderer extends EntityRenderer<LightOrb, LightOrbRenderState> {
    public static final float[][] HEAD_BUFFER = VertexUtil.Ball.getIcosphereVertexBuffer(1, 2, true);
    private final RandomSource random = RandomSource.create();
    private static final int FULL_BRIGHT = 15728880;

    public LightOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void submit(LightOrbRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        var scale = renderState.scale;

        var r = 0.2f;
        var g = 0.8f;
        var b = 1.0f;

        var bufferSource = BloomEffect.getAfter();
        var additiveBuilder = bufferSource.getBuffer(Render.RenderTypes.POS_COLOR_TRANGLES_BLOOM_ADDITIVE);

        random.setSeed(System.currentTimeMillis() / 40);
        var cameraRotation = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();

        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);

        nodeCollector.submitCustomGeometry(
                poseStack,
                Render.RenderTypes.POS_COLOR_TRANGLES,
                (pose, vertexConsumer) -> {
                    pose.rotate(cameraRotation);
                    pose.scale(0.35f, 0.35f, 0.35f);
                    BallRenderer.renderBall(
                            pose,
                            vertexConsumer,
                            HEAD_BUFFER,
                            1.0f, 1.0f, 1.0f, 1.0f,
                            FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY
                    );
                }
        );

        // 内层辉光
        poseStack.pushPose();
        poseStack.mulPose(cameraRotation);
        var pulse = 1.0f + random.nextFloat() * 0.1f;
        poseStack.scale(0.7f * pulse, 0.7f * pulse, 0.7f * pulse);
        BallRenderer.renderBall(
                poseStack.last(), additiveBuilder, HEAD_BUFFER,
                r, g, b, 0.6f,
                FULL_BRIGHT, OverlayTexture.NO_OVERLAY
        );
        poseStack.popPose();

        // 中层辉光
        poseStack.pushPose();
        poseStack.mulPose(cameraRotation);
        poseStack.scale(1.5f, 1.5f, 1.5f);
        BallRenderer.renderBall(
                poseStack.last(), additiveBuilder, HEAD_BUFFER,
                r, g, b, 0.3f,
                FULL_BRIGHT, OverlayTexture.NO_OVERLAY
        );
        poseStack.popPose();

        // 外层辉光
        poseStack.pushPose();
        poseStack.mulPose(cameraRotation);
        var outerScale = 3.0f + random.nextFloat() * 0.5f;
        poseStack.scale(outerScale, outerScale, outerScale);
        BallRenderer.renderBall(
                poseStack.last(), additiveBuilder, HEAD_BUFFER,
                r, g, b, 0.1f, // Alpha 0.1
                FULL_BRIGHT, OverlayTexture.NO_OVERLAY
        );
        poseStack.popPose();

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