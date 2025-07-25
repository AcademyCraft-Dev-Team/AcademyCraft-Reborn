package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import org.academy.api.client.renderer.BakedModelRenderer;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public class ThrownCoinRenderer extends ThrownItemRenderer<ThrownCoin> {
    public ThrownCoinRenderer(EntityRendererProvider.Context context) {
        super(context, 1.0f, false);
    }

    @Override
    public void render(ThrownCoin entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        var itemStack = entity.getItem();
        var minecraft = Minecraft.getInstance();
        var bakedModel = minecraft.getItemRenderer().getItemModelShaper().getItemModel(itemStack);
        var randomSource = RandomSource.create();
        entity.renderAngle = MathUtil.lerpStartEndFactor(entity.renderAngle, entity.angle, ClientUtil.animationFactor(1));
        bakedModel.getTransforms().ground.apply(false, poseStack);
        poseStack.mulPose(Axis.YP.rotationDegrees(entityYaw));
        var matrix4f = new Matrix4f();

        var x = 0.5f;
        var y = 0.5f;
        var z = 0.5f;
        
        matrix4f.rotateX(entity.renderAngle);
        matrix4f.translate(-x, -y, -z);
        poseStack.mulPose(matrix4f);
        BakedModelRenderer.render(poseStack, bakedModel, buffer, randomSource, false, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRender(@NotNull ThrownCoin livingEntity, @NotNull Frustum camera, double camX, double camY, double camZ) {
        return livingEntity.getDeltaMovement().length() < 2;
    }
}