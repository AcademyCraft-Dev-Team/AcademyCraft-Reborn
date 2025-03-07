package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.academy.api.client.util.RenderUtil;
import org.academy.internal.common.world.item.AcademyCraftItems;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public class AbilityDeveloperBlockEntityRenderer implements BlockEntityRenderer<AbilityDeveloperBlockEntity> {
    @Override
    public void render(@NotNull AbilityDeveloperBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (blockEntity.isMain()) {
            poseStack.pushPose();
            ItemStack itemStack = new ItemStack(AcademyCraftItems.ABILITY_DEVELOPER_BLOCK_ITEM.asItem());
            Minecraft minecraft = Minecraft.getInstance();
            BakedModel bakedModel = minecraft.getItemRenderer().getItemModelShaper().getItemModel(itemStack);
            RandomSource randomSource = RandomSource.create();
            randomSource.setSeed(42L);
            Matrix4f matrix4f = new Matrix4f();
            matrix4f.translate(0.5f, 0, 0.5f);
            float yRot;
            switch (blockEntity.getBlockState().getValue(AbilityDeveloperBlock.FACING)) {
                case NORTH -> yRot = 180;
                case EAST -> yRot = 90;
                case WEST -> yRot = 270;
                default -> yRot = 0;
            }
            matrix4f.rotateY((float) Math.toRadians(yRot));
            matrix4f.translate(-0.5f, 0, -0.5f);
            poseStack.mulPoseMatrix(matrix4f);
            RenderUtil.BakedModelRenderer.render(poseStack, bakedModel, buffer, randomSource, false, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
    }
}