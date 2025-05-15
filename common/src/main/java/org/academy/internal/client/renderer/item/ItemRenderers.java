package org.academy.internal.client.renderer.item;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import org.academy.api.client.renderer.ItemRenderer;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.client.util.RenderUtil;
import org.academy.internal.client.renderer.blockentity.WindGenBaseBlockEntityRenderer;
import org.academy.internal.client.renderer.blockentity.WindGenTopBlockEntityRenderer;
import org.academy.internal.common.world.item.Items;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

public class ItemRenderers {
    public static final Map<Item, ItemRenderer> ITEM_RENDERER_MAP = new HashMap<>();
    public static final ItemRenderer WIND_GEN_BASE_BLOCK_ITEM_RENDERER =
            (itemStack, displayContext, leftHand, poseStack,
             buffer, combinedLight, combinedOverlay, model) -> {
                poseStack.pushPose();
                poseStack.last().normal().rotateY((float) Math.toRadians(90));
                Matrix4f matrix4f = new Matrix4f();
                matrix4f.scale(0.5f);
                matrix4f.rotateX((float) Math.toRadians(180));
                matrix4f.rotateY((float) Math.toRadians(180));
                matrix4f.translate(0, -0.75f, 0);
                if (displayContext.firstPerson()) {
                    matrix4f.scale(0.5f);
                    matrix4f.translate(0.5f, -0.5f, 0);
                }
                poseStack.mulPoseMatrix(matrix4f);
                WindGenBaseBlockEntityRenderer.MODEL
                        .render(poseStack, buffer, combinedLight, combinedOverlay);
                poseStack.popPose();
            };
    public static final ItemRenderer WIND_GEN_TOP_BLOCK_ITEM_RENDERER =
            (itemStack, displayContext, leftHand, poseStack,
             buffer, combinedLight, combinedOverlay, model) -> {
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
            };

    public static final ItemRenderer WIND_GEN_PILLAR_BLOCK_RENDERER =
            (itemStack, displayContext, leftHand, poseStack,
             buffer, combinedLight, combinedOverlay, model) -> {
                poseStack.pushPose();
                Minecraft minecraft = Minecraft.getInstance();
                BakedModel bakedModel = minecraft.getItemRenderer().getItemModelShaper().getItemModel(Items.WIND_GEN_PILLAR_BLOCK_ITEM);
                RandomSource randomSource = RandomSource.create();
                randomSource.setSeed(42L);
                assert bakedModel != null;
                Matrix4f matrix4f = new Matrix4f();
                matrix4f.scale(0.5f);
                matrix4f.translate(-0.5f, -0.5f, -0.5f);
                poseStack.mulPoseMatrix(matrix4f);
                RenderUtil.BakedModelRenderer.render(poseStack, bakedModel, buffer, randomSource, false, combinedLight, combinedOverlay);
                poseStack.popPose();
            };

    static {
        ITEM_RENDERER_MAP.put(Items.WIND_GEN_BASE_BLOCK_ITEM, WIND_GEN_BASE_BLOCK_ITEM_RENDERER);
        ITEM_RENDERER_MAP.put(Items.WIND_GEN_TOP_BLOCK_ITEM, WIND_GEN_TOP_BLOCK_ITEM_RENDERER);
        ITEM_RENDERER_MAP.put(Items.WIND_GEN_PILLAR_BLOCK_ITEM, WIND_GEN_PILLAR_BLOCK_RENDERER);
    }

    public static void init() {
        for (Item item : ITEM_RENDERER_MAP.keySet()) {
            RendererManager.ITEM_RENDERER_MAP.put(item, ITEM_RENDERER_MAP.get(item));
        }
    }

    private ItemRenderers() {
    }
}