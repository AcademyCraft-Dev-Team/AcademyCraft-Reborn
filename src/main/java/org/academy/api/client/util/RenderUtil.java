package org.academy.api.client.util;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Random;

import static net.minecraft.client.renderer.RenderStateShard.*;

public final class RenderUtil {
    public static void translateToForward(final PoseStack poseStack, final LivingEntity livingEntity, final float distance) {
        final Vec3 lookVec = livingEntity.getLookAngle();

        poseStack.translate(lookVec.x * distance, lookVec.y * distance, lookVec.z * distance);
    }

    public static final class RayRenderer {
        public static final RenderType.CompositeRenderType RAY_RENDER_TYPE = RenderType.create(
                "ray_render_type",
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.TRIANGLE_STRIP,
                1024,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .createCompositeState(false)
        );

        /**
         * 按照指定面数生渲染圆柱
         *
         * @param poseStack         PoseStack
         * @param multiBufferSource MultiBufferSource
         * @param red               红色
         * @param green             绿色
         * @param blue              蓝色
         * @param alpha             透明度
         * @param yBottom           底部 Y 坐标
         * @param yTop              顶部 Y 坐标
         * @param radius            半径
         * @param faces             面数
         */
        public static void renderRay(final PoseStack poseStack, final MultiBufferSource multiBufferSource, final float red, final float green, float blue, float alpha, final float yBottom, final float yTop, final float radius, final int faces) {
            final PoseStack.Pose pose = poseStack.last();
            final Matrix4f matrix4f = pose.pose();
            final VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RAY_RENDER_TYPE);
            final double angleStep = 2 * Math.PI / faces;
            for (int i = 0; i <= faces; i++) {
                final double angle = i * angleStep;
                final float x = (float) (radius * Math.cos(angle));
                final float z = (float) (radius * Math.sin(angle));
                vertexConsumer.vertex(matrix4f, x, yTop, z).color(red, green, blue, alpha).endVertex();
                vertexConsumer.vertex(matrix4f, x, yBottom, z).color(red, green, blue, alpha).endVertex();
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
            final VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
            final PoseStack.Pose pose = poseStack.last();
            final Matrix4f matrix4f = pose.pose();
            final Matrix3f matrix3f = pose.normal();
            final float[][] edges = {{(float) box.minX, (float) box.minY, (float) box.minZ, (float) box.maxX, (float) box.minY, (float) box.minZ}, {(float) box.maxX, (float) box.minY, (float) box.minZ, (float) box.maxX, (float) box.minY, (float) box.maxZ}, {(float) box.maxX, (float) box.minY, (float) box.maxZ, (float) box.minX, (float) box.minY, (float) box.maxZ}, {(float) box.minX, (float) box.minY, (float) box.maxZ, (float) box.minX, (float) box.minY, (float) box.minZ}, {(float) box.minX, (float) box.maxY, (float) box.minZ, (float) box.maxX, (float) box.maxY, (float) box.minZ}, {(float) box.maxX, (float) box.maxY, (float) box.minZ, (float) box.maxX, (float) box.maxY, (float) box.maxZ}, {(float) box.maxX, (float) box.maxY, (float) box.maxZ, (float) box.minX, (float) box.maxY, (float) box.maxZ}, {(float) box.minX, (float) box.maxY, (float) box.maxZ, (float) box.minX, (float) box.maxY, (float) box.minZ}, {(float) box.minX, (float) box.minY, (float) box.minZ, (float) box.minX, (float) box.maxY, (float) box.minZ}, {(float) box.maxX, (float) box.minY, (float) box.minZ, (float) box.maxX, (float) box.maxY, (float) box.minZ}, {(float) box.maxX, (float) box.minY, (float) box.maxZ, (float) box.maxX, (float) box.maxY, (float) box.maxZ}, {(float) box.minX, (float) box.minY, (float) box.maxZ, (float) box.minX, (float) box.maxY, (float) box.maxZ}};
            for (float[] edge : edges) {
                vertexConsumer.vertex(matrix4f, edge[0], edge[1], edge[2]).color(r, g, b, a).normal(matrix3f, 0, 1, 0).endVertex();
                vertexConsumer.vertex(matrix4f, edge[3], edge[4], edge[5]).color(r, g, b, a).normal(matrix3f, 0, 1, 0).endVertex();
            }
        }
    }

    public static final class ArcRenderer {
        public static final ResourceLocation ARC_TEXTURE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/skill/effect/electromaster/line_segment.png");
        public static final RenderType.CompositeRenderType ARC_RENDER_TYPE = RenderType.create(
                "arc_render_type",
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                512,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                ARC_TEXTURE,
                                false,
                                false
                        ))
                        .setShaderState(POSITION_TEX_SHADER)
                        .setCullState(NO_CULL)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );

        public static void renderArc(PoseStack ps, MultiBufferSource mbs, long seed, float sx, float sy, float sz, float ex, float ey, float ez, float radius, int segments) {
            VertexConsumer vc = mbs.getBuffer(ARC_RENDER_TYPE);
            Matrix4f m = ps.last().pose();
            Random rnd = new Random(seed);
            float dx = ex - sx, dy = ey - sy, dz = ez - sz, len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float nx = dx / len, ny = dy / len, nz = dz / len;
            float hw = radius * 0.5f, step = 1.0f / segments;
            float az = -nx;
            float bz = -nx;
            float prevLx = sx, prevLy = sy, prevLz = sz;
            float prevRx = sx, prevRy = sy, prevRz = sz;
            for (int i = 0; i <= segments; i++) {
                float t = i * step;
                float ang = rnd.nextFloat() * (float) (2 * Math.PI);
                float cosA = (float) Math.cos(ang), sinA = (float) Math.sin(ang);
                float ox = (ny * cosA + ny * sinA) * radius;
                float oy = (nz * cosA + nz * sinA) * radius;
                float oz = (az * cosA + bz * sinA) * radius;
                float cx = sx + dx * t + ox;
                float cy = sy + dy * t + oy;
                float cz = sz + dz * t + oz;
                float wx = ny * oz - nz * oy;
                float wy = nz * ox - nx * oz;
                float wz = nx * oy - ny * ox;
                float normLength = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
                float invNormLength = 1.0f / (normLength + 1e-6f);
                wx *= invNormLength;
                wy *= invNormLength;
                wz *= invNormLength;
                float lx = cx - wx * hw, ly = cy - wy * hw, lz = cz - wz * hw;
                float rx = cx + wx * hw, ry = cy + wy * hw, rz = cz + wz * hw;
                vc.vertex(m, prevLx, prevLy, prevLz).uv(0, 0).endVertex();
                vc.vertex(m, prevRx, prevRy, prevRz).uv(0, 1).endVertex();
                vc.vertex(m, rx, ry, rz).uv(1, 1).endVertex();
                vc.vertex(m, lx, ly, lz).uv(1, 0).endVertex();
                prevLx = lx;
                prevLy = ly;
                prevLz = lz;
                prevRx = rx;
                prevRy = ry;
                prevRz = rz;
            }
        }
    }

    private RenderUtil() {
    }
}