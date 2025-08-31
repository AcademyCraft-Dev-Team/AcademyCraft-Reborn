package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.renderer.CylinderRenderer;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.client.util.VertexUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.client.renderer.entity.state.RailgunRayRenderState;
import org.academy.internal.common.world.entity.skill.RailgunRay;
import org.joml.Matrix4f;

public class RailgunRayRenderer extends EntityRenderer<RailgunRay, RailgunRayRenderState> {
    public static final float[][] BUFFERED_VERTEX = VertexUtil.Cylinder.getCylinderVertexBuffer(0, 1, 0.5f, 16, true);

    @Override
    public void render(RailgunRayRenderState renderState, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        renderState.renderProgress = MathUtil.lerpStartEndFactor(renderState.renderProgress, renderState.progress, ClientUtil.animationFactor(MathUtil.PI / 2));
        poseStack.mulPose(new Matrix4f()
                .rotateY((float) Math.toRadians(90 - renderState.yRot))
                .rotateZ((float) Math.toRadians(90 + renderState.xRot))
        );
        poseStack.scale(renderState.renderProgress * 0.1f, 50, renderState.renderProgress * 0.1f);
        CylinderRenderer.renderCylinder(poseStack, BloomEffect.BUFFER_SOURCE, BUFFERED_VERTEX, 0.75f, 0.5f, 0, 1f);
        poseStack.popPose();
    }

    @Override
    public RailgunRayRenderState createRenderState() {
        return new RailgunRayRenderState();
    }

    public RailgunRayRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
}