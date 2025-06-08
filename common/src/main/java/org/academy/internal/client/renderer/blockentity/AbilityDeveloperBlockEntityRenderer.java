package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
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
    public void render(@NotNull AbilityDeveloperBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (blockEntity.isMain()) {
            poseStack.pushPose();
            Direction facing = blockEntity.getBlockState().getValue(AbilityDeveloperBlock.FACING);
            float yRot = facing.getOpposite().toYRot();

            poseStack.translate(0, 1.5f, 1);
            poseStack.mulPose(Axis.XP.rotationDegrees(180));
            poseStack.rotateAround(Axis.YP.rotationDegrees(yRot), 0.5f, 0, 0.5f);
            poseStack.translate(0.5f, 0, 0);

            MODEL.setupAnim(blockEntity, partialTick);
            VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
            MODEL.setupAnim(blockEntity, partialTick);
            MODEL.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay, 1f, 1f, 1f, 1f);
            poseStack.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull AbilityDeveloperBlockEntity blockEntity) {
        return true;
    }

    @Override
    public boolean shouldRender(@NotNull AbilityDeveloperBlockEntity blockEntity, @NotNull Vec3 cameraPos) {
        return true;
    }
}