package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.client.model.OmniCraftingTableModel;
import org.academy.internal.common.world.level.block.entity.OmniCraftingTableBlockEntity;

public class OmniCraftingTableBlockEntityRenderer implements BlockEntityRenderer<OmniCraftingTableBlockEntity> {
    public static final BlockEntityRenderer<OmniCraftingTableBlockEntity> INSTANCE = new OmniCraftingTableBlockEntityRenderer();
    public static final OmniCraftingTableModel MODEL = new OmniCraftingTableModel(OmniCraftingTableModel.createBodyLayer().bakeRoot());

    @Override
    public void render(OmniCraftingTableBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, Vec3 cameraPos) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        poseStack.rotateAround(Axis.XP.rotationDegrees(180), 0, 0, 0);
        MODEL.setupAnim(blockEntity, partialTick);
        MODEL.render(poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private OmniCraftingTableBlockEntityRenderer() {
    }
}