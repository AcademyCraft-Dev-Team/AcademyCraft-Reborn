package org.academy.api.client.util;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Random;
import java.util.function.Function;

import static net.minecraft.client.renderer.RenderStateShard.*;

public final class RenderUtil {
    public static void applyOffset(final PoseStack poseStack, final Vec3 vec3) {
        poseStack.translate(vec3.x, vec3.y, vec3.z);
    }

    public static void applyCameraOffset(final PoseStack poseStack, final CameraType cameraType, final Vec3 lookVec) {
        switch (cameraType) {
            case THIRD_PERSON_BACK -> applyOffset(poseStack, lookVec.scale(4));
            case THIRD_PERSON_FRONT -> applyOffset(poseStack, lookVec.scale(-4));
        }
    }

    public static final class RingRenderer {
        public static final Function<ResourceLocation, RenderType> RING_RENDER_TYPE = resourceLocation -> RenderType.create(
                "ring_render_type",
                DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS,
                256,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(new TextureStateShard(
                                resourceLocation,
                                false, false
                        ))
                        .setShaderState(RenderStateShard.POSITION_COLOR_TEX_SHADER)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );

        public static float[][][] getVerticalVertexBuffer(float radius, float height, int segments) {
            float[][][] vertexBuffer = new float[segments][4][4];
            final float pi = (float) Math.PI;

            for (int i = 0; i < segments; i++) {
                float angle1 = (i * 2 * pi) / segments;
                float angle2 = ((i + 1) * 2 * pi) / segments;
                float u0 = (float) i / segments;
                float u1 = (float) (i + 1) / segments;

                float x1 = (float) Math.cos(angle1) * radius;
                float z1 = (float) Math.sin(angle1) * radius;
                float x2 = (float) Math.cos(angle2) * radius;
                float z2 = (float) Math.sin(angle2) * radius;

                vertexBuffer[i][0][0] = x1;
                vertexBuffer[i][0][1] = 0;
                vertexBuffer[i][0][2] = z1;
                vertexBuffer[i][0][3] = u0;

                vertexBuffer[i][1][0] = x2;
                vertexBuffer[i][1][1] = 0;
                vertexBuffer[i][1][2] = z2;
                vertexBuffer[i][1][3] = u1;

                vertexBuffer[i][2][0] = x2;
                vertexBuffer[i][2][1] = height;
                vertexBuffer[i][2][2] = z2;
                vertexBuffer[i][2][3] = u1;

                vertexBuffer[i][3][0] = x1;
                vertexBuffer[i][3][1] = height;
                vertexBuffer[i][3][2] = z1;
                vertexBuffer[i][3][3] = u0;
            }
            return vertexBuffer;
        }

        /**
         * Renders a vertical ring (standing in the XY plane) with texture.
         *
         * @param poseStack PoseStack for transformations.
         * @param buffer    MultiBufferSource for drawing.
         * @param radius    Inner radius of the ring.
         * @param height    Height of the ring band.
         * @param segments  Number of segments to approximate the circle.
         * @param texture   ResourceLocation of the texture to apply.
         */
        public static void renderVerticalRing(PoseStack poseStack, MultiBufferSource buffer, float radius, float height, int segments, ResourceLocation texture, float red, float green, float blue, float alpha) {
            float[][][] vertexBuffer = getVerticalVertexBuffer(radius, height, segments);
            renderVerticalRing(poseStack, buffer, segments, vertexBuffer, texture, red, green, blue, alpha);
        }

        public static void renderVerticalRing(PoseStack poseStack, MultiBufferSource buffer, int segments, float[][][] vertexBuffer, ResourceLocation texture, float red, float green, float blue, float alpha) {
            final PoseStack.Pose pose = poseStack.last();
            final Matrix4f matrix = pose.pose();
            final VertexConsumer vertexConsumer = buffer.getBuffer(RING_RENDER_TYPE.apply(texture));

            renderVerticalRing(matrix, vertexConsumer, segments, vertexBuffer, red, green, blue, alpha);
        }

        public static void renderVerticalRing(Matrix4f matrix, VertexConsumer vertexConsumer, int segments, float[][][] vertexBuffer, float red, float green, float blue, float alpha) {
            for (int i = 0; i < segments; i++) {
                vertexConsumer.vertex(matrix, vertexBuffer[i][0][0], vertexBuffer[i][0][1], vertexBuffer[i][0][2])
                        .color(red, green, blue, alpha)
                        .uv(vertexBuffer[i][0][3], 0)
                        .endVertex();

                vertexConsumer.vertex(matrix, vertexBuffer[i][1][0], vertexBuffer[i][1][1], vertexBuffer[i][1][2])
                        .color(red, green, blue, alpha)
                        .uv(vertexBuffer[i][1][3], 0)
                        .endVertex();

                vertexConsumer.vertex(matrix, vertexBuffer[i][2][0], vertexBuffer[i][2][1], vertexBuffer[i][2][2])
                        .color(red, green, blue, alpha)
                        .uv(vertexBuffer[i][2][3], 1)
                        .endVertex();

                vertexConsumer.vertex(matrix, vertexBuffer[i][3][0], vertexBuffer[i][3][1], vertexBuffer[i][3][2])
                        .color(red, green, blue, alpha)
                        .uv(vertexBuffer[i][3][3], 1)
                        .endVertex();
            }
        }
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