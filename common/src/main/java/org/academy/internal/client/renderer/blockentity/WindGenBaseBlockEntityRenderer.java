package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import org.academy.api.client.util.RenderUtil;
import org.academy.internal.client.gui.world.WindGenWorldGUI;
import org.academy.internal.client.model.WindGenBaseModel;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.entity.WindGenBaseBlockEntity;
import org.jetbrains.annotations.NotNull;

public class WindGenBaseBlockEntityRenderer implements BlockEntityRenderer<WindGenBaseBlockEntity> {
    public static final BlockEntityRenderer<WindGenBaseBlockEntity> INSTANCE = new WindGenBaseBlockEntityRenderer();
    public static final WindGenBaseModel MODEL = new WindGenBaseModel(WindGenBaseModel.createBodyLayer().bakeRoot());

    private WindGenBaseBlockEntityRenderer() {
    }

    @Override
    public void render(@NotNull WindGenBaseBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        if (blockEntity.isMain()) {
            Direction facing = blockEntity.getBlockState().getValue(AbilityDeveloperBlock.FACING);
            float yRot = facing.getOpposite().toYRot();

            poseStack.pushPose();
            poseStack.translate(0.5f, 1.5f, 0.5f);
            poseStack.mulPose(Axis.XP.rotationDegrees(180));
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot));

            MODEL.setupAnim(blockEntity, partialTick);
            MODEL.render(poseStack, buffer, packedLight, packedOverlay);

            if (blockEntity.windGenWorldGUI != null && blockEntity.isDisplayActive) {
                float width = 1f;
                float scale = width / WindGenWorldGUI.WIDTH;
                Minecraft minecraft = Minecraft.getInstance();
                GuiGraphics guiGraphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());

                poseStack.pushPose();
                poseStack.translate(0, 0.3075, 0.625);
                poseStack.mulPose(Axis.XP.rotationDegrees(17.5f));

                AABB aabb = new AABB(-0.5, -5.0 / 16.0, -0.05, 0.5, 5.0 / 16.0, 0.05);
                RenderUtil.LineBoxRenderer.renderWireframeBox(poseStack, buffer, aabb, 1f, 1f, 1f, 1f);

                poseStack.mulPose(Axis.XP.rotationDegrees(180));
                poseStack.translate(0,0,-0.0575f);
                poseStack.scale(-scale, -scale, scale);
                poseStack.translate(-WindGenWorldGUI.WIDTH / 2, -WindGenWorldGUI.HEIGHT / 2, 0);

                guiGraphics.pose().last().pose().mul(poseStack.last().pose());
                blockEntity.windGenWorldGUI.render(guiGraphics, partialTick);

                poseStack.popPose();
            }
            poseStack.popPose();

        } else {
            Minecraft minecraft = Minecraft.getInstance();
            BakedModel bakedModel = minecraft.getModelManager().getBlockModelShaper().getBlockModel(Blocks.WIND_GEN_PILLAR.defaultBlockState());
            RandomSource randomSource = RandomSource.create();
            randomSource.setSeed(42L);
            RenderUtil.BakedModelRenderer.render(poseStack, bakedModel, buffer, randomSource, false, packedLight, packedOverlay);
        }
        poseStack.popPose();
    }
}