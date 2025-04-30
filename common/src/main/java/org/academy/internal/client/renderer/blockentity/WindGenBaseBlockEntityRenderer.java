package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import org.academy.api.client.util.RenderUtil;
import org.academy.internal.client.gui.world.WindGenWorldGUI;
import org.academy.internal.client.models.WindGenBaseModel;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.entity.WindGenBaseBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public class WindGenBaseBlockEntityRenderer implements BlockEntityRenderer<WindGenBaseBlockEntity> {
    public static final WindGenBaseModel MODEL = new WindGenBaseModel(WindGenBaseModel.createBodyLayer().bakeRoot());

    @Override
    public void render(@NotNull WindGenBaseBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (blockEntity.isMain()) {
            poseStack.pushPose();
            {
                poseStack.last().normal().rotateX((float) Math.toRadians(180));
                Matrix4f matrix4f = new Matrix4f();

                float yRot;
                switch (blockEntity.getBlockState().getValue(AbilityDeveloperBlock.FACING)) {
                    case NORTH -> yRot = 180;
                    case EAST -> yRot = 270;
                    case WEST -> yRot = 90;
                    default -> yRot = 0;
                }
                matrix4f.rotateX((float) Math.toRadians(180));
                matrix4f.translate(0.5f, -1.5f, -0.5f);

                matrix4f.rotateY((float) Math.toRadians(yRot));

                poseStack.mulPoseMatrix(matrix4f);
                MODEL.setupAnim(blockEntity, partialTick);
                MODEL.render(poseStack, buffer, packedLight, packedOverlay);
            }
            {
                if (blockEntity.windGenWorldGUI != null && blockEntity.activeState.getAccumulatedTime() > 1291) {
                    float width = 1f;
                    float scale = width / WindGenWorldGUI.WIDTH;
                    Minecraft minecraft = Minecraft.getInstance();
                    GuiGraphics guiGraphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
                    guiGraphics.pose().mulPoseMatrix(poseStack.last().pose());
                    Matrix4f matrix4f = new Matrix4f();
                    matrix4f.translate(0.5f, 0, 0.575f);
                    matrix4f.rotateX((float) Math.toRadians(17.5f));
                    matrix4f.rotateY((float) Math.toRadians(180)).scale(scale);
                    guiGraphics.pose().mulPoseMatrix(matrix4f);
                    blockEntity.windGenWorldGUI.render(guiGraphics, partialTick);
                }
            }
            poseStack.popPose();
        } else {
            poseStack.pushPose();
            Minecraft minecraft = Minecraft.getInstance();
            BakedModel bakedModel = minecraft.getModelManager().getBlockModelShaper().getBlockModel(Blocks.WIND_GEN_PILLAR_BLOCK.defaultBlockState());
            RandomSource randomSource = RandomSource.create();
            randomSource.setSeed(42L);
            RenderUtil.BakedModelRenderer.render(poseStack, bakedModel, buffer, randomSource, false, packedLight, packedOverlay);
            poseStack.popPose();
        }
    }
}