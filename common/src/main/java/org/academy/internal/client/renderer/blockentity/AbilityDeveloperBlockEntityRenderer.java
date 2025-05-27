package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import org.joml.Matrix4f;

public class AbilityDeveloperBlockEntityRenderer implements BlockEntityRenderer<AbilityDeveloperBlockEntity> {
    public static final AbilityDeveloperBlockEntityModel ABILITY_DEVELOPER_BLOCK_ENTITY_MODEL = new AbilityDeveloperBlockEntityModel(AbilityDeveloperBlockEntityModel.createBodyLayer().bakeRoot());
    private static final ResourceLocation TEXTURE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/model/ability_developer.png");

    @Override
    public void render(@NotNull AbilityDeveloperBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (blockEntity.isMain()) {
            poseStack.pushPose();
            Matrix4f matrix4f = new Matrix4f();
            float yRot;
            switch (blockEntity.getBlockState().getValue(AbilityDeveloperBlock.FACING)) {
                case NORTH -> yRot = 180;
                case EAST -> yRot = 270;
                case WEST -> yRot = 90;
                default -> yRot = 0;
            }

            poseStack.last().normal().rotateX((float) Math.toRadians(180));
            poseStack.last().normal().rotateY((float) Math.toRadians(yRot));
            matrix4f.rotateX((float) Math.toRadians(180));
            matrix4f.translate(0.5f, -1.5f, -1.5f);

            matrix4f.translate(0, 0, 1);
            matrix4f.rotateY((float) Math.toRadians(yRot));
            matrix4f.translate(0, 0, -1);

            poseStack.mulPoseMatrix(matrix4f);

            VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
            ABILITY_DEVELOPER_BLOCK_ENTITY_MODEL.setupAnim(blockEntity, partialTick);
            ABILITY_DEVELOPER_BLOCK_ENTITY_MODEL.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay, 1f, 1f, 1f, 1f);
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