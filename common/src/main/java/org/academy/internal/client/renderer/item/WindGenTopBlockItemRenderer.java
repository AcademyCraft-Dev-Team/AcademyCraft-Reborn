package org.academy.internal.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.academy.api.client.renderer.ItemRenderer;
import org.academy.internal.client.renderer.blockentity.WindGenTopBlockEntityRenderer;
import org.joml.Matrix4f;

public class WindGenTopBlockItemRenderer implements ItemRenderer {
    @Override
    public void render(ItemStack itemStack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay, BakedModel model) {
        poseStack.pushPose();
        poseStack.last().normal().rotateX((float) Math.toRadians(180));
        Matrix4f matrix4f = new Matrix4f();
        matrix4f.rotateX((float) Math.toRadians(180));
        matrix4f.scale(0.35f);
        if (displayContext.firstPerson()) {
            matrix4f.scale(0.75f);
            matrix4f.translate(0, -1.5f, 0);
            matrix4f.rotateY((float) Math.toRadians(270));
        }
        switch (displayContext) {
            case GUI -> {
                matrix4f.scale(0.5f);
                matrix4f.translate(0.5f, 0, 0);
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
                poseStack.last().normal().rotateX((float) Math.toRadians(90));

                matrix4f.rotateY((float) Math.toRadians(180));
                matrix4f.rotateZ((float) Math.toRadians(270));
                matrix4f.rotateX((float) Math.toRadians(270));
                matrix4f.translate(0, -1, 0);
            }
        }
        poseStack.mulPoseMatrix(matrix4f);
        WindGenTopBlockEntityRenderer.MODEL
                .render(poseStack, buffer, combinedLight, combinedOverlay);
        poseStack.popPose();
    }
}
