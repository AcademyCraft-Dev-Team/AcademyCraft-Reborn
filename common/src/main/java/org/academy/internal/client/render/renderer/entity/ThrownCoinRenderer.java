package org.academy.internal.client.render.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.academy.api.client.util.RenderUtil;
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
        ItemStack itemStack = entity.getItem();
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel bakedModel = minecraft.getItemRenderer().getItemModelShaper().getItemModel(itemStack);
        RandomSource randomSource = RandomSource.create();
        randomSource.setSeed(42L);
        Matrix4f matrix4f = new Matrix4f();
        entity.renderAngle = MathUtil.lerpStartEndFactor(entity.renderAngle, entity.angle, partialTick);
        matrix4f.rotateX(entity.renderAngle);
        poseStack.mulPoseMatrix(matrix4f);
        RenderUtil.BakedModelRenderer.render(poseStack, bakedModel, buffer, randomSource, false, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRender(@NotNull ThrownCoin livingEntity, @NotNull Frustum camera, double camX, double camY, double camZ) {
        return livingEntity.getDeltaMovement().length() < 2;
    }
}