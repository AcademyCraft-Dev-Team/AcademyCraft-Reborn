package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.render.RenderTypes;
import org.academy.internal.client.model.AbilityDeveloperModel;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;

public class AbilityDeveloperBlockEntityRenderer implements BlockEntityRenderer<AbilityDeveloperBlockEntity> {
    public static final BlockEntityRenderer<AbilityDeveloperBlockEntity> INSTANCE = new AbilityDeveloperBlockEntityRenderer();
    public static final AbilityDeveloperModel MODEL = new AbilityDeveloperModel(AbilityDeveloperModel.createBodyLayer().bakeRoot());

    @Override
    public void render(AbilityDeveloperBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, Vec3 cameraPos) {
        if (blockEntity.isMain()) {
            poseStack.pushPose();
            var facing = blockEntity.getBlockState().getValue(AbilityDeveloperBlock.FACING);
            var yRot = facing.getOpposite().toYRot();

            poseStack.translate(0, 1.5f, 1);
            poseStack.mulPose(Axis.XP.rotationDegrees(180));
            poseStack.rotateAround(Axis.YP.rotationDegrees(yRot), 0.5f, 0, 0.5f);
            poseStack.translate(0.5f, 0, 0);

            MODEL.setupAnim(blockEntity, partialTick);
            var vc = bufferSource.getBuffer(RenderTypes.ABILITY_DEVELOPER);
            MODEL.setupAnim(blockEntity, partialTick);
            MODEL.renderToBuffer(poseStack, vc, packedLight, packedOverlay);
            poseStack.popPose();
        }
    }

    private AbilityDeveloperBlockEntityRenderer() {
    }
}