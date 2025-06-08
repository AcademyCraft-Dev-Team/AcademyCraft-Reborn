package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import org.academy.internal.client.model.OmniCraftingTableModel;
import org.academy.internal.common.world.level.block.entity.OmniCraftingTableBlockEntity;
import org.jetbrains.annotations.NotNull;

public class OmniCraftingTableBlockEntityRenderer implements BlockEntityRenderer<OmniCraftingTableBlockEntity> {
    public static final BlockEntityRenderer<OmniCraftingTableBlockEntity> INSTANCE = new OmniCraftingTableBlockEntityRenderer();
    public static final OmniCraftingTableModel MODEL = new OmniCraftingTableModel(OmniCraftingTableModel.createBodyLayer().bakeRoot());

    private OmniCraftingTableBlockEntityRenderer() {
    }

    @Override
    public void render(@NotNull OmniCraftingTableBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        poseStack.rotateAround(Axis.XP.rotationDegrees(180), 0, 0, 0);
        MODEL.setupAnim(blockEntity, partialTick);
        MODEL.render(poseStack, buffer, packedLight, packedOverlay);
        poseStack.popPose();
    }
}