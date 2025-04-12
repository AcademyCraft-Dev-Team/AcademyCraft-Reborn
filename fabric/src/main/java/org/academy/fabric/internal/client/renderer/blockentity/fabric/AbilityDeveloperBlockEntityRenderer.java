package org.academy.fabric.internal.client.renderer.blockentity.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.fabric.internal.common.world.level.block.entity.fabric.AbilityDeveloperBlockEntityFabric;
import org.academy.internal.client.models.AbilityDeveloperBlockEntityModel;
import org.jetbrains.annotations.NotNull;

public class AbilityDeveloperBlockEntityRenderer implements BlockEntityRenderer<AbilityDeveloperBlockEntityFabric> {
    public static final AbilityDeveloperBlockEntityModel<?> ABILITY_DEVELOPER_BLOCK_ENTITY_MODEL = new AbilityDeveloperBlockEntityModel<>(AbilityDeveloperBlockEntityModel.createBodyLayer().bakeRoot());
    private static final ResourceLocation TEXTURE = new ResourceLocation(AcademyCraft.MOD_ID,"textures/model/texture1.png");

    @Override
    public void render(@NotNull AbilityDeveloperBlockEntityFabric blockEntity, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (blockEntity.isMain()) {
          /*  poseStack.pushPose();
            Minecraft minecraft = Minecraft.getInstance();
            BakedModel bakedModel = minecraft.getModelManager().getBlockModelShaper().getBlockModel(blockEntity.getBlockState());
            RandomSource randomSource = RandomSource.create();
            randomSource.setSeed(42L);
            Matrix4f matrix4f = new Matrix4f();
            matrix4f.translate(0.5f, 0, 0.5f);
            float yRot;
            switch (blockEntity.getBlockState().getValue(AbilityDeveloperBlockFabric.FACING)) {
                case NORTH -> yRot = 180;
                case EAST -> yRot = 90;
                case WEST -> yRot = 270;
                default -> yRot = 0;
            }
            matrix4f.rotateY((float) Math.toRadians(yRot));
            matrix4f.translate(-0.5f, 0, -0.5f);
            poseStack.mulPoseMatrix(matrix4f);
            RenderUtil.BakedModelRenderer.render(poseStack, bakedModel, buffer, randomSource, false, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();*/
            VertexConsumer vertexConsumer = buffer.getBuffer(ABILITY_DEVELOPER_BLOCK_ENTITY_MODEL.renderType(TEXTURE));
            ABILITY_DEVELOPER_BLOCK_ENTITY_MODEL.renderToBuffer(poseStack,vertexConsumer,packedLight,packedOverlay,1f,1f,1f,1f);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull AbilityDeveloperBlockEntityFabric blockEntity) {
        return true;
    }

    @Override
    public boolean shouldRender(@NotNull AbilityDeveloperBlockEntityFabric blockEntity, @NotNull Vec3 cameraPos) {
        return true;
    }
}