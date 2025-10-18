package org.academy.internal.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.Render;
import org.academy.api.client.Resource;
import org.academy.internal.client.model.ImagiphaseDowsingRodModel;
import org.academy.internal.common.world.item.ImagiphaseDowsingRodItem;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Set;

public final class ImagiphaseDowsingRodSpecialRenderer implements NoDataSpecialModelRenderer {
    public static final ImagiphaseDowsingRodSpecialRenderer INSTANCE = new ImagiphaseDowsingRodSpecialRenderer();
    public static final ImagiphaseDowsingRodModel MODEL = new ImagiphaseDowsingRodModel(ImagiphaseDowsingRodModel.createBodyLayer().bakeRoot());
    private static final float MAP_SCALE = 0.00065f;
    private static final float PIXEL_SIZE = 1.0f * MAP_SCALE * 8;

    private ImagiphaseDowsingRodSpecialRenderer() {
    }

    private static void fillQuad(VertexConsumer consumer, Matrix4f matrix,
                                 float x0, float y0, float z0,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float x3, float y3, float z3,
                                 float r, float g, float b, float a) {
        consumer.addVertex(matrix, x0, y0, z0).setColor(r, g, b, a);
        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
        consumer.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a);
    }

    private static void render3DMap(PoseStack poseStack, VertexConsumer vertexConsumer) {
        final var player = Minecraft.getInstance().player;
        if (player == null) return;

        final var playerPos = player.position();
        final var targetPositions = ImagiphaseDowsingRodItem.RENDER_TARGET_POSITIONS;

        poseStack.pushPose();

        poseStack.mulPose(Axis.YN.rotationDegrees(player.getYRot()));

        var zOffset = 0.15f;
        poseStack.translate(0, -0.25f, zOffset);
        poseStack.mulPose(Axis.YN.rotationDegrees(-player.getYRot()));
        poseStack.scale(1, 1, 1);

        final var matrix = poseStack.last().pose();

        final var playerMarkerHalfSizeX = PIXEL_SIZE / 2.0f;
        final var playerMarkerHalfSizeZ = PIXEL_SIZE / 2.0f;

        addCuboid(vertexConsumer, matrix, -playerMarkerHalfSizeX / 2, 8 * PIXEL_SIZE, -playerMarkerHalfSizeZ / 2,
                playerMarkerHalfSizeX, PIXEL_SIZE, playerMarkerHalfSizeZ,
                1.0f, 1.0f, 1.0f, 1.0f);

        for (var sectionOrigin : targetPositions) {
            final var relativePos = Vec3.atLowerCornerOf(sectionOrigin).subtract(playerPos);
            final var mapX = (float) relativePos.x() * MAP_SCALE;
            final var mapY = (float) relativePos.y() * MAP_SCALE;
            final var mapZ = (float) relativePos.z() * MAP_SCALE;

            final var r = (Math.abs(sectionOrigin.getX() % 10)) / 10.0f;
            final var g = (Math.abs(sectionOrigin.getY() % 10)) / 10.0f;
            final var b = (Math.abs(sectionOrigin.getZ() % 10)) / 10.0f;

            addCube(vertexConsumer, matrix, mapX, mapY, mapZ, PIXEL_SIZE, r, g, b, 1);
        }

        poseStack.popPose();
    }

    private static void addCube(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float size, float r, float g, float b, float a) {
        final var hs = size / 2;
        addCuboid(consumer, matrix, x, y, z, hs, hs, hs, r, g, b, a);
    }

    private static void addCuboid(VertexConsumer consumer, Matrix4f matrix,
                                  float centerX, float centerY, float centerZ,
                                  float halfSizeX, float halfSizeY, float halfSizeZ,
                                  float r, float g, float b, float a) {
        final var x0 = centerX - halfSizeX;
        final var x1 = centerX + halfSizeX;
        final var y0 = centerY - halfSizeY;
        final var y1 = centerY + halfSizeY;
        final var z0 = centerZ - halfSizeZ;
        final var z1 = centerZ + halfSizeZ;

        fillQuad(consumer, matrix, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
        fillQuad(consumer, matrix, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, r, g, b, a);
        fillQuad(consumer, matrix, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, r, g, b, a);
        fillQuad(consumer, matrix, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a);
        fillQuad(consumer, matrix, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, r, g, b, a);
        fillQuad(consumer, matrix, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, r, g, b, a);
    }

    @Override
    public void getExtents(Set<Vector3f> output) {
    }

    @Override
    public void submit(ItemDisplayContext displayContext, PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, int packedOverlay, boolean hasFoil, int outlineColor) {
        poseStack.pushPose();

        poseStack.mulPose(Axis.XP.rotationDegrees(180));

        if (displayContext == ItemDisplayContext.GUI) {
            poseStack.translate(1.35f, -2.5f, 0);
            poseStack.mulPose(Axis.YP.rotationDegrees(90));
            poseStack.scale(1.5f, 1.5f, 1.5f);
        } else {
            if (displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
                poseStack.translate(1.5f, -1.5f, -1);
                poseStack.mulPose(Axis.YP.rotationDegrees(235));
            }
            if (displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
                poseStack.translate(1.5f, -1.5f, 0);
                poseStack.mulPose(Axis.YP.rotationDegrees(125));
            }
            if (displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
                poseStack.mulPose(Axis.YP.rotationDegrees(25));
                poseStack.mulPose(Axis.ZP.rotationDegrees(-75));
                poseStack.translate(0.75f, 0, -0.125f);
            }
            if (displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
                poseStack.mulPose(Axis.YP.rotationDegrees(145));
                poseStack.mulPose(Axis.ZP.rotationDegrees(-75));
                poseStack.translate(0.5f, -0.75f, 0.5f);
            }
        }

        nodeCollector.submitModel(MODEL, Unit.INSTANCE, poseStack, MODEL.renderType(Resource.Textures.IMAG_PHASE_DOWSING_ROD), packedLight, packedOverlay, outlineColor, null);
        if (displayContext.firstPerson()) {
            nodeCollector.submitCustomGeometry(poseStack, Render.RenderTypes.POS_COLOR_QUADS_BLOOM, (pose, consumer) -> render3DMap(new PoseStack(), consumer));
        }
        poseStack.popPose();
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked {
        public static final Unbaked INSTANCE = new Unbaked();
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            return ImagiphaseDowsingRodSpecialRenderer.INSTANCE;
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}