package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import org.academy.api.client.util.RenderUtil;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.entity.WindGenPillarBlockEntity;
import org.jetbrains.annotations.NotNull;

public class WindGenPillarBlockEntityRenderer implements BlockEntityRenderer<WindGenPillarBlockEntity> {
    @Override
    public void render(@NotNull WindGenPillarBlockEntity windGenPillarBlockEntity, float v, @NotNull PoseStack poseStack, @NotNull MultiBufferSource multiBufferSource, int i, int i1) {
        poseStack.pushPose();
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel bakedModel = minecraft.getModelManager().getBlockModelShaper().getBlockModel(Blocks.WIND_GEN_PILLAR_BLOCK.defaultBlockState());
        RandomSource randomSource = RandomSource.create();
        randomSource.setSeed(42L);
        RenderUtil.BakedModelRenderer.render(poseStack, bakedModel, multiBufferSource, randomSource, false, i, i1);
        poseStack.popPose();
    }
}
