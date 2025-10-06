package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.client.renderer.entity.state.ThrownCoinRenderState;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.academy.internal.common.world.item.Items;
import org.joml.Matrix4f;

import java.util.List;

public class ThrownCoinRenderer extends EntityRenderer<ThrownCoin, ThrownCoinRenderState> {
    public ThrownCoinRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void submit(ThrownCoinRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        if (renderState.thrownCoin != null) {
            poseStack.pushPose();
            var matrix4f = new Matrix4f();
            renderState.thrownCoin.renderAngle = MathUtil.lerpStartEndFactor(renderState.thrownCoin.renderAngle, renderState.thrownCoin.angle, ClientUtil.animationFactor(1));
            poseStack.mulPose(Axis.YP.rotationDegrees(renderState.thrownCoin.getYRot()));

            matrix4f.rotateX(renderState.thrownCoin.renderAngle);
            matrix4f.translate(0, -0.125f, 0);
            poseStack.mulPose(matrix4f);
      //      ItemRenderer.renderItem(Items.COIN.get().getDefaultInstance(), ItemDisplayContext.GROUND, packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer, List.of(), 0);
            poseStack.popPose();
        }
    }

    @Override
    public ThrownCoinRenderState createRenderState() {
        return new ThrownCoinRenderState();
    }

    @Override
    public void extractRenderState(ThrownCoin entity, ThrownCoinRenderState reusedState, float partialTick) {
        super.extractRenderState(entity, reusedState, partialTick);
        reusedState.thrownCoin = entity;
    }

    @Override
    public boolean shouldRender(ThrownCoin livingEntity, Frustum camera, double camX, double camY, double camZ) {
        return livingEntity.getDeltaMovement().length() < 2;
    }
}