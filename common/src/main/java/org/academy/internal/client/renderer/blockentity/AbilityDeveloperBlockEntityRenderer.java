package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.internal.client.model.AbilityDeveloperBlockEntityModel;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.jetbrains.annotations.NotNull;

public class AbilityDeveloperBlockEntityRenderer implements BlockEntityRenderer<AbilityDeveloperBlockEntity> {
    public static final BlockEntityRenderer<AbilityDeveloperBlockEntity> INSTANCE = new AbilityDeveloperBlockEntityRenderer();
    public static final AbilityDeveloperBlockEntityModel MODEL = new AbilityDeveloperBlockEntityModel(AbilityDeveloperBlockEntityModel.createBodyLayer().bakeRoot());
    private static final ResourceLocation TEXTURE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/model/ability_developer.png");

    private AbilityDeveloperBlockEntityRenderer() {
    }

    @Override
    public void render(@NotNull AbilityDeveloperBlockEntity newBlockEntity, float partialTick, @NotNull PoseStack newPoseStack, @NotNull MultiBufferSource newBuffer, int packedLight, int packedOverlay) {
        if (newBlockEntity.isMain()) {
            newPoseStack.pushPose();
            var facing = newBlockEntity.getBlockState().getValue(AbilityDeveloperBlock.FACING);
            var yRot = facing.getOpposite().toYRot();

            newPoseStack.translate(0, 1.5f, 1);
            newPoseStack.mulPose(Axis.XP.rotationDegrees(180));
            newPoseStack.rotateAround(Axis.YP.rotationDegrees(yRot), 0.5f, 0, 0.5f);
            newPoseStack.translate(0.5f, 0, 0);

            MODEL.setupAnim(newBlockEntity, partialTick);
            var vertexConsumer = newBuffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
            MODEL.setupAnim(newBlockEntity, partialTick);
            MODEL.renderToBuffer(newPoseStack, vertexConsumer, packedLight, packedOverlay, 1f, 1f, 1f, 1f);
            newPoseStack.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull AbilityDeveloperBlockEntity newBlockEntity) {
        return true;
    }

    @Override
    public boolean shouldRender(@NotNull AbilityDeveloperBlockEntity newBlockEntity, @NotNull Vec3 newCameraPos) {
        return true;
    }
}