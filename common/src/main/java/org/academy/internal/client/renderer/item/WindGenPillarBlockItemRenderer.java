package org.academy.internal.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.academy.api.client.renderer.ItemRenderer;
import org.academy.api.client.util.RenderUtil;
import org.joml.Matrix4f;

public class WindGenPillarBlockItemRenderer implements ItemRenderer {
    @Override
    public void render(ItemStack itemStack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay, BakedModel model) {
        poseStack.pushPose();
        BakedModel bakedModel = Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(itemStack);
        RandomSource randomSource = RandomSource.create();
        Matrix4f matrix4f = new Matrix4f();
        matrix4f.scale(0.5f);
        matrix4f.translate(-0.5f, -0.5f, -0.5f);
        poseStack.mulPoseMatrix(matrix4f);
        RenderUtil.BakedModelRenderer.render(poseStack, bakedModel, buffer, randomSource, false, combinedLight, combinedOverlay);
        poseStack.popPose();
    }
}
