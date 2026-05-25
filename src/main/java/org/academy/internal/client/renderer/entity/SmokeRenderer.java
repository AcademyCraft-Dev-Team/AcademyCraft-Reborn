package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.client.render.post.PostEffect;
import org.academy.internal.client.renderer.entity.state.SmokeRenderState;
import org.academy.internal.common.world.entity.skill.Smoke;

public class SmokeRenderer extends EntityRenderer<Smoke, SmokeRenderState> {
    public static final Identifier TEXTURE = AcademyCraft.academy("textures/ability/generic/effect/smokes.png");

    public SmokeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public SmokeRenderState createRenderState() {
        return new SmokeRenderState();
    }

    @Override
    public void extractRenderState(Smoke entity, SmokeRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.alpha = entity.getAlpha();
        state.size = entity.size;
        state.frame = entity.frame;
    }

    @Override
    public void submit(SmokeRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        poseStack.pushPose();
        renderState.renderCount++;

        if (entityRenderDispatcher.camera != null) {
            poseStack.mulPose(entityRenderDispatcher.camera.rotation());
        }
        poseStack.scale(renderState.size, renderState.size, 1.0f);

        var matrix = poseStack.last().pose();
        var packedLight = renderState.lightCoords;
        var vertexConsumer = PostEffect.BUFFER_SOURCE_PRE.getBuffer(RenderTypes.eyes(TEXTURE));

        float halfSize = 1.0f;
        float r = 1.0f, g = 1.0f, b = 1.0f, a = renderState.alpha;

        int frame = Math.clamp(renderState.frame, 0, 3);
        int col = frame % 2;
        int row = frame / 2;
        float size = 0.5f;
        float u0 = col * size;
        float v0 = row * size;
        float u1 = u0 + size;
        float v1 = v0 + size;

        vertexConsumer.addVertex(matrix, halfSize, -halfSize, 0).setColor(r, g, b, a).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, halfSize, halfSize, 0).setColor(r, g, b, a).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, -halfSize, halfSize, 0).setColor(r, g, b, a).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, -halfSize, -halfSize, 0).setColor(r, g, b, a).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);

        poseStack.popPose();
    }
}