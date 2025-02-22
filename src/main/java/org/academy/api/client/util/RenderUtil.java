package org.academy.api.client.util;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Random;

import static net.minecraft.client.renderer.RenderStateShard.*;

public final class RenderUtil {
    public static final RenderType.CompositeRenderType GLOWING_CYLINDER = RenderType.create("glowing_cylinder", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP, 10240, false, true, RenderType.CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).createCompositeState(false));

    public static void translateToForward(final PoseStack poseStack, final LivingEntity livingEntity, final float distance) {
        final Vec3 lookVec = livingEntity.getLookAngle();

        poseStack.translate(lookVec.x * distance, lookVec.y * distance, lookVec.z * distance);
    }

    public static void addVertex(final Matrix4f matrix4f, final Matrix3f matrix3f, final VertexConsumer vertexConsumer, final float r, final float g, final float b, final float a, final float x, final float y, final float z, final float nx, final float ny, final float nz) {
        vertexConsumer.vertex(matrix4f, x, y, z).color(r, g, b, a).overlayCoords(OverlayTexture.NO_OVERLAY).normal(matrix3f, nx, ny, nz).endVertex();
    }

    public static void addLine(VertexConsumer vertexConsumer, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        vertexConsumer.vertex(pose.pose(), x1, y1, z1).color(r, g, b, a).normal(pose.normal(), 0, 1, 0).endVertex();
        vertexConsumer.vertex(pose.pose(), x2, y2, z2).color(r, g, b, a).normal(pose.normal(), 0, 1, 0).endVertex();
    }

    public static final class RayRenderer {
        /**
         * 按照指定面数生渲染圆柱
         *
         * @param poseStack      PoseStack
         * @param vertexConsumer VertexConsumer
         * @param red            红色
         * @param green          绿色
         * @param blue           蓝色
         * @param alpha          透明度
         * @param yBottom        底部 Y 坐标
         * @param yTop           顶部 Y 坐标
         * @param radius         半径
         * @param faces          面数
         */
        public static void renderRay(final PoseStack poseStack, final VertexConsumer vertexConsumer, final float red, final float green, float blue, float alpha, final float yBottom, final float yTop, final float radius, final int faces) {
            final PoseStack.Pose pose = poseStack.last();
            final Matrix4f matrix4f = pose.pose();
            final Matrix3f matrix3f = pose.normal();

            final double angleStep = 2 * Math.PI / faces;

            for (int i = 0; i <= faces; i++) {
                final double angle = i * angleStep;

                final float x = (float) (radius * Math.cos(angle));
                final float z = (float) (radius * Math.sin(angle));

                addVertex(matrix4f, matrix3f, vertexConsumer, red, green, blue, alpha, x, yTop, z, 0, 1, 0);
                addVertex(matrix4f, matrix3f, vertexConsumer, red, green, blue, alpha, x, yBottom, z, 0, 1, 0);
            }
        }
    }

    public static final class BoxRenderer {
        /**
         * 渲染一个线框方块
         *
         * @param poseStack    PoseStack
         * @param bufferSource MultiBufferSource
         * @param box          AABB 大小
         * @param r            红色
         * @param g            绿色
         * @param b            蓝色
         * @param a            透明度
         */
        public static void renderWireframeBox(PoseStack poseStack, MultiBufferSource bufferSource, AABB box, float r, float g, float b, float a) {
            VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
            PoseStack.Pose pose = poseStack.last();

            float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
            float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;

            // 十二条边的顶点
            float[][] edges = {{x1, y1, z1, x2, y1, z1}, {x2, y1, z1, x2, y1, z2}, {x2, y1, z2, x1, y1, z2}, {x1, y1, z2, x1, y1, z1}, {x1, y2, z1, x2, y2, z1}, {x2, y2, z1, x2, y2, z2}, {x2, y2, z2, x1, y2, z2}, {x1, y2, z2, x1, y2, z1}, {x1, y1, z1, x1, y2, z1}, {x2, y1, z1, x2, y2, z1}, {x2, y1, z2, x2, y2, z2}, {x1, y1, z2, x1, y2, z2}};

            for (float[] edge : edges) {
                addLine(vertexConsumer, pose, edge[0], edge[1], edge[2], edge[3], edge[4], edge[5], r, g, b, a);
            }
        }
    }

    public static final class LightningRenderer {
        /**
         * 渲染闪电/电弧，但是比原版更加自定义
         *
         * @param poseStack PoseStack
         * @param bufferSource MultiBufferSource
         * @param seed 随机路径种子
         * @param r 红色
         * @param g 绿色
         * @param b 蓝色
         * @param alpha 透明度
         * @param startY 起点
         * @param endY 终点
         * @param size 大小
         * @param segments 边数
         * @param points 细分点
         * @param amplitude 幅度
         */
        public static void renderLightning(PoseStack poseStack, MultiBufferSource bufferSource, long seed, float r, float g, float b, float alpha, float startY, float endY, float size, int segments, int points, float amplitude) {
            VertexConsumer vertexConsumer = bufferSource.getBuffer(GLOWING_CYLINDER);
            Matrix4f matrix4f = poseStack.last().pose();
            Matrix3f matrix3f = poseStack.last().normal();

            RandomSource randomSource = RandomSource.create(seed);
            float[] lightningX = new float[points];
            float[] lightningY = new float[points];
            float[] lightningZ = new float[points];

            float dy = (endY - startY) / (points - 1);

            lightningX[0] = 0;
            lightningY[0] = startY;
            lightningZ[0] = 0;
            for (int i = 1; i < points - 1; i++) {
                lightningX[i] = (randomSource.nextFloat() - 0.5f) * amplitude;
                lightningY[i] = startY + dy * i + (randomSource.nextFloat() - 0.5f) * amplitude;
                lightningZ[i] = (randomSource.nextFloat() - 0.5f) * amplitude;
            }
            lightningX[points - 1] = 0;
            lightningY[points - 1] = endY;
            lightningZ[points - 1] = 0;

            for (int i = 1; i < points; i++) {
                float sx = lightningX[i - 1], sy = lightningY[i - 1], sz = lightningZ[i - 1];
                float ex = lightningX[i], ey = lightningY[i], ez = lightningZ[i];
                float segDx = ex - sx;
                float segDy = ey - sy;
                float segDz = ez - sz;

                for (int j = 0; j <= segments; j++) {
                    double angle = (j * Math.PI * 2) / segments;
                    float nx = (float) Math.cos(angle) * size;
                    float nz = (float) Math.sin(angle) * size;

                    addVertex(matrix4f, matrix3f, vertexConsumer, r, g, b, alpha, sx + nx, sy, sz + nz, segDx, segDy, segDz);
                    addVertex(matrix4f, matrix3f, vertexConsumer, r, g, b, alpha, ex + nx, ey, ez + nz, segDx, segDy, segDz);
                }
            }
        }
    }
}