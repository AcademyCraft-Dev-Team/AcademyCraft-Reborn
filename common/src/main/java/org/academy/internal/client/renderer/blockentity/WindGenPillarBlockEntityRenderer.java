package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.util.RandomSource;
import org.academy.api.client.renderer.BakedModelRenderer;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.entity.WindGenPillarBlockEntity;
import org.jetbrains.annotations.NotNull;

public class WindGenPillarBlockEntityRenderer implements BlockEntityRenderer<WindGenPillarBlockEntity> {
    public static final BlockEntityRenderer<WindGenPillarBlockEntity> INSTANCE = new WindGenPillarBlockEntityRenderer();

    private WindGenPillarBlockEntityRenderer() {
    }

    @Override
    public void render(@NotNull WindGenPillarBlockEntity newWindGenPillarBlockEntity, float v, @NotNull PoseStack newPoseStack, @NotNull MultiBufferSource newMultiBufferSource, int i, int i1) {
        newPoseStack.pushPose();
        var minecraft = Minecraft.getInstance();
        var bakedModel = minecraft.getModelManager().getBlockModelShaper().getBlockModel(Blocks.WIND_GEN_PILLAR.defaultBlockState());
        var randomSource = RandomSource.create();
        randomSource.setSeed(42L);
        BakedModelRenderer.render(newPoseStack, bakedModel, newMultiBufferSource, randomSource, false, i, i1);
        newPoseStack.popPose();
    }
}