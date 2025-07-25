package org.academy.internal.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.academy.api.client.renderer.ItemRenderer;

import static org.academy.internal.client.renderer.blockentity.OmniCraftingTableBlockEntityRenderer.MODEL;

public class OmniCraftingTableBlockItemRenderer  implements ItemRenderer {
    public static final ItemRenderer INSTANCE = new OmniCraftingTableBlockItemRenderer();

    private OmniCraftingTableBlockItemRenderer() {
    }

    @Override
    public void render(ItemStack itemStack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay, BakedModel model) {
        poseStack.pushPose();
        BakedModel bakedModel = Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(Items.STONE.getDefaultInstance());
        bakedModel.getTransforms().getTransform(displayContext).apply(leftHand, poseStack);
        poseStack.translate(0, -0.5f, 0);
        poseStack.rotateAround(Axis.XP.rotationDegrees(180), 0, 0, 0);
        MODEL.render(poseStack, buffer, combinedLight, combinedOverlay);
        poseStack.popPose();
    }
}