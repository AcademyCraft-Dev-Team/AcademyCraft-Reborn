package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.academy.AcademyCraft;
import org.academy.api.client.render.post.PostEffect;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.client.renderer.entity.state.SmokeRenderState;
import org.academy.internal.common.world.entity.skill.Smoke;

public class SmokeRenderer extends EntityRenderer<Smoke, SmokeRenderState> {
    public static final Identifier TEXTURE = AcademyCraft.academy("textures/ability/generic/effect/smokes.png");

    @Override
    public void submit(SmokeRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        poseStack.pushPose();
        renderState.renderCount++;
        renderState.renderAlpha = Mth.lerp(ClientUtil.animationFactor(MathUtil.PI / 2), renderState.renderAlpha, renderState.alpha);

        var size = 0.5f;
        var halfSize = 1f;
        var r = 1f;
        var g = 1f;
        var b = 1f;
        var a = renderState.renderAlpha;

        if (entityRenderDispatcher.camera != null) {
            poseStack.mulPose(entityRenderDispatcher.camera.rotation());
        }
        var matrix = poseStack.last().pose();
        var packedLight = renderState.lightCoords;
        var vertexConsumer = PostEffect.BUFFER_SOURCE_PRE.getBuffer(RenderTypes.eyes(TEXTURE));

        var frame = Math.max(0, Math.min(renderState.frame, 3));
        var col = frame % 2;
        var row = frame / 2;

        var u0 = col * size;
        var v0 = row * size;
        var u1 = u0 + size;
        var v1 = v0 + size;

        vertexConsumer.addVertex(matrix, -halfSize, -halfSize, 0).setColor(r, g, b, a).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, -halfSize, halfSize, 0).setColor(r, g, b, a).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, halfSize, halfSize, 0).setColor(r, g, b, a).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, halfSize, -halfSize, 0).setColor(r, g, b, a).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);

        poseStack.popPose();
    }

    @Override
    public SmokeRenderState createRenderState() {
        return new SmokeRenderState();
    }

    public SmokeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
}