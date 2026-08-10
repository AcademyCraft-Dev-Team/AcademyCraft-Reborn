package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.academy.api.client.resources.R;
import org.academy.internal.client.renderer.entity.state.DarkmatterCutSlashRenderState;
import org.academy.internal.common.world.entity.skill.DarkmatterCutSlash;
import org.joml.Matrix4f;

public final class DarkmatterCutSlashRenderer
        extends EntityRenderer<DarkmatterCutSlash, DarkmatterCutSlashRenderState> {
    private static final Identifier[] FRAMES = {
            R.textures.darkmatter_cut_slash_effect_1,
            R.textures.darkmatter_cut_slash_effect_2,
            R.textures.darkmatter_cut_slash_effect_3,
            R.textures.darkmatter_cut_slash_effect_4
    };

    public DarkmatterCutSlashRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    private static void renderSlash(VertexConsumer consumer, Matrix4f matrix, int light,
                                    float alpha, float width, float height, boolean mirrored) {
        var halfWidth = width * 0.5f;
        var halfHeight = height * 0.5f;
        var topV = mirrored ? 1.0f : 0.0f;
        var bottomV = mirrored ? 0.0f : 1.0f;

        addVertex(consumer, matrix, -halfWidth, halfHeight, -halfWidth, 0, topV, alpha, light);
        addVertex(consumer, matrix, halfWidth, halfHeight, -halfWidth, 1, topV, alpha, light);
        addVertex(consumer, matrix, halfWidth, halfHeight, halfWidth, 1, bottomV, alpha, light);
        addVertex(consumer, matrix, -halfWidth, halfHeight, halfWidth, 0, bottomV, alpha, light);

        addVertex(consumer, matrix, -halfWidth, halfHeight, halfWidth, 0, bottomV, alpha, light);
        addVertex(consumer, matrix, halfWidth, halfHeight, halfWidth, 1, bottomV, alpha, light);
        addVertex(consumer, matrix, halfWidth, halfHeight, -halfWidth, 1, topV, alpha, light);
        addVertex(consumer, matrix, -halfWidth, halfHeight, -halfWidth, 0, topV, alpha, light);
    }

    private static void addVertex(VertexConsumer consumer, Matrix4f matrix,
                                  float x, float y, float z, float u, float v,
                                  float alpha, int light) {
        consumer.addVertex(matrix, x, y, z).setColor(1, 1, 1, alpha)
                .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(0, 1, 0);
    }

    @Override
    public void submit(DarkmatterCutSlashRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        var swingProgress = Mth.clamp(state.progress * 1.7f, 0.0f, 1.0f);
        var life = Mth.sin(state.progress * Mth.PI);
        var alpha = life * 0.92f;
        if (alpha <= 0.001f) return;
        var frame = Mth.clamp((int) (state.progress * FRAMES.length), 0, FRAMES.length - 1);
        var scale = state.scale * (0.92f + 0.18f * life);
        var width = 5.0f * scale * (0.82f + 0.18f * swingProgress);
        var height = 1.05f * scale;
        var mirrored = state.direction < 0;

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(270 - state.yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(-state.xRot));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(FRAMES[frame]),
                (pose, consumer) -> {
                    renderSlash(consumer, pose.pose(), state.lightCoords, alpha,
                            width, height, mirrored);
                    renderSlash(consumer, pose.pose(), state.lightCoords, alpha * 0.52f,
                            width * 0.76f, height * 0.76f, !mirrored);
                });
        poseStack.popPose();
    }

    @Override
    public DarkmatterCutSlashRenderState createRenderState() {
        return new DarkmatterCutSlashRenderState();
    }

    @Override
    public void extractRenderState(DarkmatterCutSlash entity,
                                   DarkmatterCutSlashRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.progress = Math.clamp((entity.tickCount + partialTick) / entity.getDuration(), 0, 1);
        state.scale = entity.getScale();
        state.xRot = entity.getXRot();
        state.yRot = entity.getYRot();
        state.direction = entity.getSwingDirection();
    }

    @Override
    protected boolean affectedByCulling(DarkmatterCutSlash entity) {
        return false;
    }
}
