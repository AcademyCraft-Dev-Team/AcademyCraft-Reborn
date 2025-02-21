package org.academy.api.client.util;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
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

        poseStack.translate(
                lookVec.x * distance,
                lookVec.y * distance,
                lookVec.z * distance
        );
    }

    public static void addVertex(final Matrix4f matrix4f, final Matrix3f matrix3f, final VertexConsumer vertexConsumer, final float r, final float g, final float b, final float a, final float x, final float y, final float z, final float nx, final float ny, final float nz) {
        vertexConsumer.vertex(matrix4f, x, y, z).color(r, g, b, a).overlayCoords(OverlayTexture.NO_OVERLAY).normal(matrix3f, nx, ny, nz).endVertex();
    }

    private static void addLine(VertexConsumer vertexConsumer, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
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
            float[][] edges = {
                    {x1, y1, z1, x2, y1, z1}, {x2, y1, z1, x2, y1, z2}, {x2, y1, z2, x1, y1, z2}, {x1, y1, z2, x1, y1, z1},
                    {x1, y2, z1, x2, y2, z1}, {x2, y2, z1, x2, y2, z2}, {x2, y2, z2, x1, y2, z2}, {x1, y2, z2, x1, y2, z1},
                    {x1, y1, z1, x1, y2, z1}, {x2, y1, z1, x2, y2, z1}, {x2, y1, z2, x2, y2, z2}, {x1, y1, z2, x1, y2, z2}
            };

            for (float[] edge : edges) {
                addLine(vertexConsumer, pose, edge[0], edge[1], edge[2], edge[3], edge[4], edge[5], r, g, b, a);
            }
        }
    }

    public static final class ArcRenderer {
        private static final Random RANDOM = new Random();

        /**
         * 渲染空气中的电弧
         *
         * @param poseStack    当前矩阵堆栈
         * @param bufferSource 渲染缓冲
         * @param startPos     电弧起点
         * @param endPos       电弧终点
         * @param r            红色通道
         * @param g            绿色通道
         * @param b            蓝色通道
         * @param alpha        透明度
         * @param radius       电弧的宽度
         * @param segments     细分数（越高越平滑）
         */
        public static void renderArc(
                PoseStack poseStack, MultiBufferSource bufferSource,
                Vec3 startPos, Vec3 endPos,
                float r, float g, float b, float alpha,
                float radius, int segments
        ) {
            VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lightning()); // 使用 Minecraft 自带的闪电渲染
            PoseStack.Pose pose = poseStack.last();
            Matrix4f matrix4f = pose.pose();
            Matrix3f matrix3f = pose.normal();

            Vec3[] arcPoints = generateArcPoints(startPos, endPos, segments);

            // 遍历点，使用 Triangle Strip 绘制带状电弧
            for (int i = 0; i < arcPoints.length; i++) {
                Vec3 point = arcPoints[i];

                float x = (float) point.x;
                float y = (float) point.y;
                float z = (float) point.z;

                // 计算法线方向（让电弧有宽度）
                float nx = (float) -Math.sin(i * 0.3);
                float nz = (float) Math.cos(i * 0.3);

                float x1 = x + nx * radius;
                float z1 = z + nz * radius;
                float x2 = x - nx * radius;
                float z2 = z - nz * radius;

                addVertex(matrix4f, matrix3f, vertexConsumer, r, g, b, alpha, x1, y, z1, 1, 1, 1);
                addVertex(matrix4f, matrix3f, vertexConsumer, r, g, b, alpha, x2, y, z2, 1, 1, 1);
            }
        }

        /**
         * 生成扭曲的电弧路径
         *
         * @param startPos 起点
         * @param endPos   终点
         * @param segments 细分数
         * @return 随机扭曲的电弧路径
         */
        private static Vec3[] generateArcPoints(Vec3 startPos, Vec3 endPos, int segments) {
            Vec3[] points = new Vec3[segments + 1];
            points[0] = startPos;
            points[segments] = endPos;

            for (int i = 1; i < segments; i++) {
                double t = (double) i / segments;

                // 线性插值计算基础点
                double x = startPos.x + (endPos.x - startPos.x) * t;
                double y = startPos.y + (endPos.y - startPos.y) * t;
                double z = startPos.z + (endPos.z - startPos.z) * t;

                // 随机偏移，制造“跳跃感”
                double xOffset = (RANDOM.nextDouble() - 0.5) * 0.5;
                double yOffset = (RANDOM.nextDouble() - 0.5) * 0.5;
                double zOffset = (RANDOM.nextDouble() - 0.5) * 0.5;

                points[i] = new Vec3(x + xOffset, y + yOffset, z + zOffset);
            }

            return points;
        }
    }
}