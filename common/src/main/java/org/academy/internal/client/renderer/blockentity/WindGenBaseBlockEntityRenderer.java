package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.renderer.BakedModelRenderer;
import org.academy.api.client.renderer.LineBoxRenderer;
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
    public void render(@NotNull WindGenBaseBlockEntity newBlockEntity, float partialTick, @NotNull PoseStack newPoseStack, @NotNull MultiBufferSource newBuffer, int packedLight, int packedOverlay) {
        newPoseStack.pushPose();
        if (newBlockEntity.isMain()) {
            var facing = newBlockEntity.getBlockState().getValue(AbilityDeveloperBlock.FACING);
            var yRot = facing.getOpposite().toYRot();

            newPoseStack.pushPose();
            newPoseStack.translate(0.5f, 1.5f, 0.5f);
            newPoseStack.mulPose(Axis.XP.rotationDegrees(180));
            newPoseStack.mulPose(Axis.YP.rotationDegrees(yRot));

            MODEL.setupAnim(newBlockEntity, partialTick);
            MODEL.render(newPoseStack, newBuffer, packedLight, packedOverlay);

            if (newBlockEntity.windGenWorldGUI != null && newBlockEntity.isDisplayActive) {
                var width = 1f;
                var scale = width / WindGenWorldGUI.WIDTH;

                newPoseStack.pushPose();
                newPoseStack.translate(0, 0.3075, 0.625);
                newPoseStack.mulPose(Axis.XP.rotationDegrees(17.5f));

                var aabb = new AABB(-0.5, -5.0 / 16.0, -0.05, 0.5, 5.0 / 16.0, 0.05);
                LineBoxRenderer.renderWireframeBox(newPoseStack, newBuffer, aabb, 1f, 1f, 1f, 1f);

                newPoseStack.mulPose(Axis.XP.rotationDegrees(180));
                newPoseStack.translate(0,0,-0.0575f);
                newPoseStack.scale(-scale, -scale, scale);
                newPoseStack.translate(-WindGenWorldGUI.WIDTH / 2, -WindGenWorldGUI.HEIGHT / 2, 0);

                var matrixStack = new MatrixStack();
                matrixStack.setFrom(newPoseStack.last());

                newBlockEntity.windGenWorldGUI.render(matrixStack, (MultiBufferSource.BufferSource) newBuffer, partialTick);

                newPoseStack.popPose();
            }
            newPoseStack.popPose();

        } else {
            var minecraft = Minecraft.getInstance();
            var bakedModel = minecraft.getModelManager().getBlockModelShaper().getBlockModel(Blocks.WIND_GEN_PILLAR.defaultBlockState());
            var randomSource = RandomSource.create();
            randomSource.setSeed(42L);
            BakedModelRenderer.render(newPoseStack, bakedModel, newBuffer, randomSource, false, packedLight, packedOverlay);
        }
        newPoseStack.popPose();
    }
}