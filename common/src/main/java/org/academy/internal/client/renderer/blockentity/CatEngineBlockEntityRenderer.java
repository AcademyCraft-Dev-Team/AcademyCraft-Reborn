package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;
import org.academy.internal.client.model.CatEngineModel;
import org.academy.internal.common.world.level.block.entity.CatEngineBlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * @author cnlimiter
 */
public class CatEngineBlockEntityRenderer implements BlockEntityRenderer<CatEngineBlockEntity> {
    public static final BlockEntityRenderer<CatEngineBlockEntity> INSTANCE = new CatEngineBlockEntityRenderer();
    public static final CatEngineModel MODEL = new CatEngineModel(CatEngineModel.createBodyLayer().bakeRoot());
    private static final ResourceLocation TEXTURE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/block/cat_engine.png");

    public CatEngineBlockEntityRenderer() {
    }

    @Override
    public void render(@NotNull CatEngineBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        if (blockEntity.rH >= 360) {
            blockEntity.rH = 0;
        }
        float f1;
        for (f1 = blockEntity.rot - blockEntity.oRot; f1 >= (float) Math.PI; f1 -= ((float) Math.PI * 2F)) {

        }
        while (f1 < -(float) Math.PI) {
            f1 += ((float) Math.PI * 2F);
        }
        float f2 = blockEntity.oRot + f1 * partialTick;
        poseStack.rotateAround(Axis.YN.rotation(f2), 0.5f, 0.5f, 0.5f);
        poseStack.rotateAround(Axis.YN.rotation(90), 0.5f, 0.5f, 0.5f);
        if (blockEntity.enable) {
            poseStack.rotateAround(Axis.XN.rotation(blockEntity.rH += 0.2F), 0.5f, 0.5f, 0.5f);
        }
        VertexConsumer vertexconsumer = buffer.getBuffer(RenderType.entityTranslucentCull(TEXTURE));
        MODEL.renderToBuffer(poseStack, vertexconsumer, packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }
}
