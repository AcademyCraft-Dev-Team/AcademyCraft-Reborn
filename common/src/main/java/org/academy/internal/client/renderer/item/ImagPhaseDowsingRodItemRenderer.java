package org.academy.internal.client.renderer.item;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.renderer.ItemRenderer;
import org.academy.api.client.resource.TextureResources;
import org.academy.internal.client.model.ImagPhaseDowsingRodModel;
import org.academy.internal.common.world.item.ImagPhaseDowsingRodItem;
import org.joml.Matrix4f;

import java.util.List;

import static org.academy.api.client.util.RenderStateUtil.POSITION_COLOR_SHADER;
import static org.academy.api.client.util.RenderStateUtil.TRANSLUCENT_TRANSPARENCY;

public class ImagPhaseDowsingRodItemRenderer implements ItemRenderer {
    public static final ImagPhaseDowsingRodModel MODEL = new ImagPhaseDowsingRodModel(ImagPhaseDowsingRodModel.createBodyLayer().bakeRoot());
    public static final ItemRenderer INSTANCE = new ImagPhaseDowsingRodItemRenderer();
    public static final RenderType CUBE_MAP = new RenderType.CompositeRenderType(
            "cube_map",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .createCompositeState(false)
    );
    private static final float MAP_SCALE = 0.00065f;
    private static final float PIXEL_SIZE = 1.0f * MAP_SCALE * 16;

    private ImagPhaseDowsingRodItemRenderer() {
    }

    @Override
    public void render(ItemStack itemStack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay, BakedModel model) {
        poseStack.pushPose();
        poseStack.scale(0.95f, 0.95f, 0.95f);

        if (displayContext == ItemDisplayContext.GUI) {
            poseStack.translate(-0.15, 0, 0);
            poseStack.translate(0, 0.8f, 0);
        }

        if (displayContext.firstPerson()) {
            poseStack.translate(-0.25f, 0.75f, 0.25f);
        }

        if (!displayContext.firstPerson() && displayContext != ItemDisplayContext.GUI) {
            poseStack.translate(0, 0.5f, 0);
        }

        poseStack.mulPose(Axis.XP.rotationDegrees(180));

        if (displayContext != ItemDisplayContext.GUI) {
            poseStack.mulPose(Axis.YP.rotationDegrees(90));
            if (displayContext == ItemDisplayContext.FIXED) {
                poseStack.mulPose(Axis.YP.rotationDegrees(90));
            }
        }

        if (displayContext != ItemDisplayContext.GUI) {
            poseStack.scale(0.5f, 0.5f, 0.5f);
        }

        if (displayContext == ItemDisplayContext.GUI) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(30));
            poseStack.mulPose(Axis.YP.rotationDegrees(-145));
            poseStack.mulPose(Axis.XP.rotationDegrees(-45));
        }

        if (leftHand) {
            poseStack.translate(0, 0, 1);
        }

        MODEL.renderToBuffer(poseStack, buffer.getBuffer(MODEL.renderType(TextureResources.TEXTURE_IMAG_PHASE_DOWSING_ROD_MODEL)), combinedLight, combinedOverlay, 1, 1, 1, 1);

        if (!ImagPhaseDowsingRodItem.RENDER_TARGET_POSITIONS.isEmpty()
                && displayContext != ItemDisplayContext.GUI
                && displayContext != ItemDisplayContext.FIXED
                && displayContext != ItemDisplayContext.GROUND
        ) {
            render3DMap(poseStack, buffer);
        }

        poseStack.popPose();
    }

    private static void render3DMap(PoseStack poseStack, MultiBufferSource buffer) {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        final Vec3 playerPos = player.position();
        final List<BlockPos> targetPositions = ImagPhaseDowsingRodItem.RENDER_TARGET_POSITIONS;

        poseStack.pushPose();
        poseStack.setIdentity();

        poseStack.translate(0, 0, -0.65f);

        poseStack.mulPose(Axis.XP.rotationDegrees(player.getXRot()));
        poseStack.mulPose(Axis.YP.rotationDegrees(player.getYRot()));
        poseStack.scale(-1, 1, -1);

        poseStack.translate(0, -0.065f, 0);

        final VertexConsumer vertexConsumer = buffer.getBuffer(CUBE_MAP);
        final Matrix4f matrix = poseStack.last().pose();

        final float playerMarkerHalfSizeX = PIXEL_SIZE / 2.0f;
        final float playerMarkerHalfSizeY = (PIXEL_SIZE * 10.0f) / 2.0f;
        final float playerMarkerHalfSizeZ = PIXEL_SIZE / 2.0f;
        poseStack.pushPose();
        addCuboid(vertexConsumer, matrix, -playerMarkerHalfSizeX / 2, 0, -playerMarkerHalfSizeZ / 2,
                playerMarkerHalfSizeX, playerMarkerHalfSizeY, playerMarkerHalfSizeZ,
                1.0f, 1.0f, 1.0f, 1.0f);

        poseStack.popPose();

        for (BlockPos sectionOrigin : targetPositions) {
            final Vec3 relativePos = Vec3.atLowerCornerOf(sectionOrigin).subtract(playerPos);
            final float mapX = (float) relativePos.x() * MAP_SCALE;
            final float mapY = (float) relativePos.y() * MAP_SCALE;
            final float mapZ = (float) relativePos.z() * MAP_SCALE;

            final float r = 0.2f + (Math.abs(sectionOrigin.getX() % 10)) / 10.0f * 0.8f;
            final float g = 0.2f + (Math.abs(sectionOrigin.getY() % 10)) / 10.0f * 0.8f;
            final float b = 0.2f + (Math.abs(sectionOrigin.getZ() % 10)) / 10.0f * 0.8f;

            addCube(vertexConsumer, matrix, mapX, mapY, mapZ, PIXEL_SIZE, r, g, b, 0.8f);
        }

        poseStack.popPose();
    }

    private static void addCube(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float size, float r, float g, float b, float a) {
        final float hs = size / 2;
        addCuboid(consumer, matrix, x, y, z, hs, hs, hs, r, g, b, a);
    }

    private static void addCuboid(VertexConsumer consumer, Matrix4f matrix,
                                  float centerX, float centerY, float centerZ,
                                  float halfSizeX, float halfSizeY, float halfSizeZ,
                                  float r, float g, float b, float a) {
        final float x0 = centerX - halfSizeX;
        final float x1 = centerX + halfSizeX;
        final float y0 = centerY - halfSizeY;
        final float y1 = centerY + halfSizeY;
        final float z0 = centerZ - halfSizeZ;
        final float z1 = centerZ + halfSizeZ;

        fillQuad(consumer, matrix, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, r, g, b, a);
        fillQuad(consumer, matrix, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b, a);
        fillQuad(consumer, matrix, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, r, g, b, a);
        fillQuad(consumer, matrix, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0, r, g, b, a);
        fillQuad(consumer, matrix, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, a);
        fillQuad(consumer, matrix, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, r, g, b, a);
    }

    private static void fillQuad(VertexConsumer consumer, Matrix4f matrix,
                                 float x0, float y0, float z0,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float x3, float y3, float z3,
                                 float r, float g, float b, float a) {
        consumer.vertex(matrix, x0, y0, z0).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x1, y1, z1).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x2, y2, z2).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x3, y3, z3).color(r, g, b, a).endVertex();
    }
}