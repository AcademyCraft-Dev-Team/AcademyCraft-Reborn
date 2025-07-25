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
    public void render(@NotNull WindGenBaseBlockEntity newBlockEntity, float partialTick, @NotNull PoseStack ps, @NotNull MultiBufferSource newBuffer, int packedLight, int packedOverlay) {
        ps.pushPose();
        if (newBlockEntity.isMain()) {
            var facing = newBlockEntity.getBlockState().getValue(AbilityDeveloperBlock.FACING);
            var yRot = facing.getOpposite().toYRot();

            ps.pushPose();
            ps.translate(0.5f, 1.5f, 0.5f);
            ps.mulPose(Axis.XP.rotationDegrees(180));
            ps.mulPose(Axis.YP.rotationDegrees(yRot));

            MODEL.setupAnim(newBlockEntity, partialTick);
            MODEL.render(ps, newBuffer, packedLight, packedOverlay);

            if (newBlockEntity.windGenWorldGUI != null && newBlockEntity.isDisplayActive) {
                var width = 1f;
                var scale = width / WindGenWorldGUI.WIDTH;

                ps.pushPose();
                ps.translate(0, 0.3075, 0.625);
                ps.mulPose(Axis.XP.rotationDegrees(17.5f));

                var aabb = new AABB(-0.5, -5.0 / 16.0, -0.05, 0.5, 5.0 / 16.0, 0.05);
                LineBoxRenderer.renderWireframeBox(new MatrixStack().setFrom(ps.last()), newBuffer, aabb, 1f, 1f, 1f, 1f);

                ps.mulPose(Axis.XP.rotationDegrees(180));
                ps.translate(0,0,-0.0575f);
                ps.scale(-scale, -scale, scale);
                ps.translate(-WindGenWorldGUI.WIDTH / 2, -WindGenWorldGUI.HEIGHT / 2, 0);

                var matrixStack = new MatrixStack();
                matrixStack.setFrom(ps.last());

                newBlockEntity.windGenWorldGUI.render(matrixStack, (MultiBufferSource.BufferSource) newBuffer, partialTick);

                ps.popPose();
            }
            ps.popPose();

        } else {
            var minecraft = Minecraft.getInstance();
            var bakedModel = minecraft.getModelManager().getBlockModelShaper().getBlockModel(Blocks.WIND_GEN_PILLAR.get().defaultBlockState());
            var randomSource = RandomSource.create();
            randomSource.setSeed(42L);
            BakedModelRenderer.render(ps, bakedModel, newBuffer, randomSource, false, packedLight, packedOverlay);
        }
        ps.popPose();
    }
}