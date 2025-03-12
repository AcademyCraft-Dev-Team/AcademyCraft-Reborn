package org.academy.api.client.util;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Random;

import static net.minecraft.client.renderer.RenderStateShard.*;

public final class RenderUtil {
    public static void translateToForward(final PoseStack poseStack, final LivingEntity livingEntity, final float distance) {
        final Vec3 lookVec = livingEntity.getLookAngle();

        poseStack.translate(lookVec.x * distance, lookVec.y * distance, lookVec.z * distance);
    }

    public static final class BallRenderer {
        public static final RenderType.CompositeRenderType BALL_RENDER_TYPE = RenderType.create(
                "ball_render_type",
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.TRIANGLES,
                256,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .createCompositeState(false)
        );

        /**
         * 渲染一个球体
         *
         * @param poseStack PoseStack
         * @param buffer    MultiBufferSource
         * @param radius    半径
         * @param faces     细分面数（决定球体精细度）
         * @param red       颜色 - 红
         * @param green     颜色 - 绿
         * @param blue      颜色 - 蓝
         * @param alpha     透明度
         */
        @SuppressWarnings({"UnnecessaryLocalVariable", "DuplicatedCode"})
        public static void renderBall(final PoseStack poseStack, final MultiBufferSource buffer, final float radius, final int faces, float red, float green, float blue, float alpha) {
            final PoseStack.Pose pose = poseStack.last();
            final Matrix4f matrix = pose.pose();
            final VertexConsumer vertexConsumer = buffer.getBuffer(BALL_RENDER_TYPE);

            final int latBands = faces;
            final int lonBands = faces;
            final float pi = (float) Math.PI;

            for (int lat = 0; lat < latBands; lat++) {
                float theta1 = pi * (-0.5f + (float) lat / latBands);
                float theta2 = pi * (-0.5f + (float) (lat + 1) / latBands);

                float y1 = radius * (float) Math.sin(theta1);
                float y2 = radius * (float) Math.sin(theta2);

                float scale1 = radius * (float) Math.cos(theta1);
                float scale2 = radius * (float) Math.cos(theta2);

                for (int lon = 0; lon < lonBands; lon++) {
                    float phi1 = 2 * pi * (float) lon / lonBands;
                    float phi2 = 2 * pi * (float) (lon + 1) / lonBands;

                    float x1 = scale1 * (float) Math.cos(phi1);
                    float z1 = scale1 * (float) Math.sin(phi1);
                    float x2 = scale1 * (float) Math.cos(phi2);
                    float z2 = scale1 * (float) Math.sin(phi2);
                    float x3 = scale2 * (float) Math.cos(phi1);
                    float z3 = scale2 * (float) Math.sin(phi1);
                    float x4 = scale2 * (float) Math.cos(phi2);
                    float z4 = scale2 * (float) Math.sin(phi2);

                    vertexConsumer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha).endVertex();
                    vertexConsumer.vertex(matrix, x3, y2, z3).color(red, green, blue, alpha).endVertex();
                    vertexConsumer.vertex(matrix, x2, y1, z2).color(red, green, blue, alpha).endVertex();

                    vertexConsumer.vertex(matrix, x2, y1, z2).color(red, green, blue, alpha).endVertex();
                    vertexConsumer.vertex(matrix, x3, y2, z3).color(red, green, blue, alpha).endVertex();
                    vertexConsumer.vertex(matrix, x4, y2, z4).color(red, green, blue, alpha).endVertex();
                }
            }
        }
    }

    public static final class RayRenderer {
        public static final RenderType.CompositeRenderType RAY_RENDER_TYPE = RenderType.create(
                "ray_render_type",
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.TRIANGLE_STRIP,
                128,
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
                256,
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

    @SuppressWarnings("deprecation")
    public static final class BakedModelRenderer {
        public static final RenderType BAKED_MODEL_NO_TRANSPARENCY_RENDER_TYPE = RenderType.create(
                "baked_model_no_transparency_render_type",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1024,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                TextureAtlas.LOCATION_BLOCKS,
                                false,
                                true
                        ))
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setTransparencyState(NO_TRANSPARENCY)
                        .createCompositeState(true)
        );
        public static final RenderType BAKED_MODEL_TRANSPARENCY_RENDER_TYPE = RenderType.create(
                "baked_model_transparency_render_type",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1024,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                TextureAtlas.LOCATION_BLOCKS,
                                false,
                                true
                        ))
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setCullState(NO_CULL)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(true)
        );

        public static void render(PoseStack poseStack, BakedModel bakedModel, MultiBufferSource multiBufferSource, RandomSource randomSource, boolean transparency, int light, int overlay) {
            VertexConsumer vertexConsumer = multiBufferSource.getBuffer(transparency ? BAKED_MODEL_TRANSPARENCY_RENDER_TYPE : BAKED_MODEL_NO_TRANSPARENCY_RENDER_TYPE);
            List<BakedQuad> bakedQuadList = bakedModel.getQuads(null, null, randomSource);
            for (BakedQuad bakedQuad : bakedQuadList) {
                vertexConsumer.putBulkData(poseStack.last(), bakedQuad, 1, 1, 1, light, overlay);
            }
        }
    }

    private RenderUtil() {
    }
}