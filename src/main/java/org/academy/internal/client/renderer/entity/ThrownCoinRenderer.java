package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.List;

import static net.minecraft.client.renderer.entity.ItemRenderer.getFoilBufferDirect;

/**
 * Some shit.
 */
@Environment(EnvType.CLIENT)
public class ThrownCoinRenderer extends ThrownItemRenderer<ThrownCoin> {
    private final ItemRenderer itemRenderer;
    public static float a = 0.05f;
    public static float b = 0.1f;
    public static float c = 0.05f;
    private static final Matrix4f matrix = new Matrix4f();

    protected ThrownCoinRenderer(EntityRendererProvider.Context context) {
        super(context, 1.0f, false);
        this.itemRenderer = context.getItemRenderer();
    }

    private int getRenderAmount(ItemStack itemStack) {
        int i = 1;
        if (itemStack.getCount() > 48) {
            i = 5;
        } else if (itemStack.getCount() > 32) {
            i = 4;
        } else if (itemStack.getCount() > 16) {
            i = 3;
        } else if (itemStack.getCount() > 1) {
            i = 2;
        }

        return i;
    }

    @Override
    public void render(@NotNull ThrownCoin entity, float f, float g, @NotNull PoseStack poseStack, @NotNull MultiBufferSource multiBufferSource, int i) {
        ItemStack itemStack = entity.getItem();
        BakedModel bakedModel = this.itemRenderer.getModel(itemStack, entity.level(), null, entity.getId());
        int k = this.getRenderAmount(itemStack);
        for (int u = 0; u < k; u++) {
            poseStack.pushPose();
            poseStack.translate(-0.25f, -0.15f, -0.1f);
            renderItem(entity, itemStack, ItemDisplayContext.GROUND, false, poseStack, multiBufferSource, i, OverlayTexture.NO_OVERLAY, bakedModel);
            poseStack.popPose();
        }
    }

    public void renderItem(ThrownCoin entity, ItemStack itemStack, ItemDisplayContext itemDisplayContext, boolean bl, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, BakedModel bakedModel) {
        bakedModel.getTransforms().getTransform(itemDisplayContext).apply(bl, poseStack);
        RenderType renderType = ItemBlockRenderTypes.getRenderType(itemStack, true);
        VertexConsumer vertexConsumer;
        vertexConsumer = getFoilBufferDirect(multiBufferSource, renderType, true, itemStack.hasFoil());

        renderModelLists(entity, bakedModel, i, j, poseStack, vertexConsumer);
    }

    private void renderModelLists(ThrownCoin entity, BakedModel bakedModel, int i, int j, PoseStack poseStack, VertexConsumer vertexConsumer) {
        RandomSource randomSource = RandomSource.create();
        for (Direction direction : Direction.values()) {
            randomSource.setSeed(42L);
            this.renderQuadList(entity, poseStack, vertexConsumer, bakedModel.getQuads(null, direction, randomSource), i, j);
        }
        randomSource.setSeed(42L);
        this.renderQuadList(entity, poseStack, vertexConsumer, bakedModel.getQuads(null, null, randomSource), i, j);
    }

    private void renderQuadList(ThrownCoin entity, PoseStack poseStack, VertexConsumer vertexConsumer, List<BakedQuad> list, int i, int j) {
        PoseStack.Pose pose = poseStack.last();

        matrix.translate(0, 0, -0.15f);
        matrix.rotateX(0.0005f);
        matrix.translate(0, 0, 0.15f);

        poseStack.mulPoseMatrix(matrix);

        for (BakedQuad bakedQuad : list) {
            int k = -1;

            float f = (float) (k >> 16 & 0xFF) / 255.0F;
            float g = (float) (k >> 8 & 0xFF) / 255.0F;
            float h = (float) (k & 0xFF) / 255.0F;
            vertexConsumer.putBulkData(pose, bakedQuad, f, g, h, i, j);
        }
    }
}