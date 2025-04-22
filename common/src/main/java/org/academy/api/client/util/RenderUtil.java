package org.academy.api.client.util;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.client.renderer.Shaders;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;

import static net.minecraft.client.renderer.RenderStateShard.*;

@SuppressWarnings("DuplicatedCode")
public final class RenderUtil {
    private RenderUtil() {
    }

    public static void applyOffset(final PoseStack poseStack, final Vec3 vec3) {
        poseStack.translate(vec3.x, vec3.y, vec3.z);
    }

    public static void applyCameraOffset(final PoseStack poseStack, final CameraType cameraType, final Vec3 lookVec) {
        switch (cameraType) {
            case THIRD_PERSON_BACK -> applyOffset(poseStack, lookVec.scale(4));
            case THIRD_PERSON_FRONT -> applyOffset(poseStack, lookVec.scale(-4));
            default -> {
            }
        }
    }

    @NotNull
    public static RenderType getPositionTexRenderType(@NotNull String name, @NotNull ResourceLocation resourceLocation, boolean blur) {
        return new RenderType.CompositeRenderType(
                name,
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                16,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setTextureState(
                                new RenderStateShard.TextureStateShard(
                                        resourceLocation,
                                        blur,
                                        false
                                )
                        )
                        .setShaderState(RenderUtil.RenderStates.POSITION_TEX_SHADER)
                        .setCullState(RenderStates.NO_CULL)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false));
    }

    public static RenderType getPositionColorTexRenderType(@NotNull String name, @NotNull ResourceLocation resourceLocation, boolean blur) {
        return new RenderType.CompositeRenderType(
                name,
                DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS,
                16,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(
                                new RenderStateShard.TextureStateShard(
                                        resourceLocation,
                                        blur,
                                        false
                                )
                        )
                        .setShaderState(RenderUtil.RenderStates.POSITION_COLOR_TEX_SHADER)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false));
    }

    public static final class RenderStates {
        public static final WriteMaskStateShard COLOR_WRITE = new WriteMaskStateShard(true, false);
        public static final ShaderStateShard POSITION_COLOR_LIGHTMAP_SHADER = new ShaderStateShard(GameRenderer::getPositionColorLightmapShader);
        public static final ShaderStateShard POSITION_SHADER = new ShaderStateShard(GameRenderer::getPositionShader);
        public static final ShaderStateShard POSITION_COLOR_TEX_SHADER = new ShaderStateShard(GameRenderer::getPositionColorTexShader);
        public static final ShaderStateShard POSITION_TEX_SHADER = new ShaderStateShard(GameRenderer::getPositionTexShader);
        public static final ShaderStateShard POSITION_COLOR_TEX_LIGHTMAP_SHADER = new ShaderStateShard(GameRenderer::getPositionColorTexLightmapShader);
        public static final ShaderStateShard POSITION_COLOR_SHADER = new ShaderStateShard(GameRenderer::getPositionColorShader);
        public static final ShaderStateShard DEBUG = new ShaderStateShard(new Supplier<ShaderInstance>() {
            @Override
            public ShaderInstance get() {
                return Shaders.test;
            }
        });
        public static final TransparencyStateShard TRANSLUCENT_TRANSPARENCY = new TransparencyStateShard("translucent_transparency", () -> {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        }, () -> {
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        });
        public static final RenderStateShard.TransparencyStateShard LIGHTNING_TRANSPARENCY = new RenderStateShard.TransparencyStateShard("lightning_transparency", () -> {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        }, () -> {
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        });
        public static final CullStateShard CULL = new CullStateShard(true);
        public static final CullStateShard NO_CULL = new CullStateShard(false);
        public static final LightmapStateShard LIGHTMAP = new LightmapStateShard(true);
        public static final LightmapStateShard NO_LIGHTMAP = new LightmapStateShard(false);
        public static final OverlayStateShard OVERLAY = new OverlayStateShard(true);
        public static final OverlayStateShard NO_OVERLAY = new OverlayStateShard(false);
        public static final ShaderStateShard RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER = new ShaderStateShard(GameRenderer::getRendertypeEntityTranslucentCullShader);
        public static final DepthTestStateShard NO_DEPTH_TEST = new DepthTestStateShard("always", 519);
        public static final DepthTestStateShard EQUAL_DEPTH_TEST = new DepthTestStateShard("==", 514);
        public static final DepthTestStateShard LEQUAL_DEPTH_TEST = new DepthTestStateShard("<=", 515);
        public static final DepthTestStateShard GREATER_DEPTH_TEST = new DepthTestStateShard(">", 516);
    }

    public static final class GeneralRenderer {
        public static final RenderType RENDER_TYPE_POSITION_COLOR = new RenderType.CompositeRenderType(
                "render_type_position_color",
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS,
                256,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStates.POSITION_COLOR_SHADER)
                        .setTransparencyState(RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );

        public static void fill(Matrix4f matrix4f, float minX, float minY, float maxX, float maxY, int color, MultiBufferSource buffer) {
            if (minX < maxX) {
                float i = minX;
                minX = maxX;
                maxX = i;
            }

            if (minY < maxY) {
                float j = minY;
                minY = maxY;
                maxY = j;
            }

            float f3 = (float) FastColor.ARGB32.alpha(color) / 255.0F;
            float f = (float) FastColor.ARGB32.red(color) / 255.0F;
            float f1 = (float) FastColor.ARGB32.green(color) / 255.0F;
            float f2 = (float) FastColor.ARGB32.blue(color) / 255.0F;
            VertexConsumer vertexconsumer = buffer.getBuffer(RenderType.gui());
            vertexconsumer.vertex(matrix4f, minX, minY, 0).color(f, f1, f2, f3).endVertex();
            vertexconsumer.vertex(matrix4f, minX, maxY, 0).color(f, f1, f2, f3).endVertex();
            vertexconsumer.vertex(matrix4f, maxX, maxY, 0).color(f, f1, f2, f3).endVertex();
            vertexconsumer.vertex(matrix4f, maxX, minY, 0).color(f, f1, f2, f3).endVertex();
        }
    }

    public static final class RingRenderer {
        public static final Function<ResourceLocation, RenderType> RING_RENDER_TYPE = resourceLocation
                -> new RenderType.CompositeRenderType(
                "ring_render_type",
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                512,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(new TextureStateShard(resourceLocation, false, false))
                        .setShaderState(RenderStates.POSITION_TEX_SHADER)
                        .setCullState(RenderStates.NO_CULL)
                        .setOutputState(ITEM_ENTITY_TARGET)
                        .setTransparencyState(RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );

        public static void renderRing(Matrix4f matrix, VertexConsumer vertexConsumer,
                                      int segments, float[][][] vertexBuffer) {
            for (int i = 0; i < segments; i++) {
                float[] v0 = vertexBuffer[i][0];
                float[] v1 = vertexBuffer[i][1];
                float[] v2 = vertexBuffer[i][2];
                float[] v3 = vertexBuffer[i][3];

                vertexConsumer.vertex(matrix, v0[0], v0[1], v0[2]).uv(v0[3], 0).endVertex();
                vertexConsumer.vertex(matrix, v1[0], v1[1], v1[2]).uv(v1[3], 0).endVertex();
                vertexConsumer.vertex(matrix, v2[0], v2[1], v2[2]).uv(v2[3], 1).endVertex();
                vertexConsumer.vertex(matrix, v3[0], v3[1], v3[2]).uv(v3[3], 1).endVertex();
            }
        }

        public static void renderRing(PoseStack poseStack, MultiBufferSource buffer, int segments,
                                      float[][][] vertexBuffer, ResourceLocation texture) {
            if (vertexBuffer == null || vertexBuffer.length < segments || segments <= 0) return;
            final PoseStack.Pose pose = poseStack.last();
            final Matrix4f matrix = pose.pose();
            final VertexConsumer vertexConsumer = buffer.getBuffer(RING_RENDER_TYPE.apply(texture));
            renderRing(matrix, vertexConsumer, segments, vertexBuffer);
        }

        public static void renderVerticalRing(PoseStack poseStack, MultiBufferSource buffer,
                                              float radius, float height, int segments, ResourceLocation texture) {
            float[][][] vertexBuffer = VertexUtil.Ring.getVerticalVertexBuffer(radius, height, segments);
            if (vertexBuffer == null) return;
            renderRing(poseStack, buffer, segments, vertexBuffer, texture);
        }

        public static void renderHorizontalRing(PoseStack poseStack, MultiBufferSource buffer,
                                                float radius, float height, int segments, ResourceLocation texture) {
            float[][][] vertexBuffer = VertexUtil.Ring.getHorizontalVertexBuffer(radius, height, segments);
            if (vertexBuffer == null) return;
            renderRing(poseStack, buffer, segments, vertexBuffer, texture);
        }
    }

    public static final class BallRenderer {
        public static final RenderType BALL_RENDER_TYPE = new RenderType.CompositeRenderType(
                "ball_render_type",
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.TRIANGLES,
                256,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStates.POSITION_COLOR_SHADER)
                        .setTransparencyState(RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .setOutputState(ITEM_ENTITY_TARGET)
                        .setCullState(RenderStates.NO_CULL)
                        .createCompositeState(false)
        );

        public static float[][][] getBallVertexBuffer(final float radius, final int faces) {
            if (radius <= 0 || faces < 3) return null;

            final int numTriangles = faces * faces * 2;
            final float[][][] vertexBuffer = new float[numTriangles][3][3];

            final float pi = MathUtil.PI;
            final float twoPi = MathUtil.TWO_PI;
            int triangleIndex = 0;

            for (int lat = 0; lat < faces; lat++) {
                float theta1 = pi * (-0.5f + (float) lat / faces);
                float theta2 = pi * (-0.5f + (float) (lat + 1) / faces);

                float y1 = radius * (float) Math.sin(theta1);
                float y2 = radius * (float) Math.sin(theta2);

                float scale1 = radius * (float) Math.cos(theta1);
                float scale2 = radius * (float) Math.cos(theta2);

                for (int lon = 0; lon < faces; lon++) {
                    float phi1 = twoPi * (float) lon / faces;
                    float phi2 = twoPi * (float) (lon + 1) / faces;

                    float cosPhi1 = (float) Math.cos(phi1);
                    float sinPhi1 = (float) Math.sin(phi1);
                    float cosPhi2 = (float) Math.cos(phi2);
                    float sinPhi2 = (float) Math.sin(phi2);

                    float x1 = scale1 * cosPhi1;
                    float z1 = scale1 * sinPhi1;
                    float x2 = scale1 * cosPhi2;
                    float z2 = scale1 * sinPhi2;
                    float x3 = scale2 * cosPhi1;
                    float z3 = scale2 * sinPhi1;
                    float x4 = scale2 * cosPhi2;
                    float z4 = scale2 * sinPhi2;

                    vertexBuffer[triangleIndex][0][0] = x1;
                    vertexBuffer[triangleIndex][0][1] = y1;
                    vertexBuffer[triangleIndex][0][2] = z1;
                    vertexBuffer[triangleIndex][1][0] = x3;
                    vertexBuffer[triangleIndex][1][1] = y2;
                    vertexBuffer[triangleIndex][1][2] = z3;
                    vertexBuffer[triangleIndex][2][0] = x2;
                    vertexBuffer[triangleIndex][2][1] = y1;
                    vertexBuffer[triangleIndex][2][2] = z2;
                    triangleIndex++;

                    vertexBuffer[triangleIndex][0][0] = x2;
                    vertexBuffer[triangleIndex][0][1] = y1;
                    vertexBuffer[triangleIndex][0][2] = z2;
                    vertexBuffer[triangleIndex][1][0] = x3;
                    vertexBuffer[triangleIndex][1][1] = y2;
                    vertexBuffer[triangleIndex][1][2] = z3;
                    vertexBuffer[triangleIndex][2][0] = x4;
                    vertexBuffer[triangleIndex][2][1] = y2;
                    vertexBuffer[triangleIndex][2][2] = z4;
                    triangleIndex++;
                }
            }
            return vertexBuffer;
        }

        @SuppressWarnings({"UnnecessaryLocalVariable", "DuplicatedCode"})
        public static void renderBall(final PoseStack poseStack, final MultiBufferSource buffer,
                                      final float radius, final int faces,
                                      float red, float green, float blue, float alpha) {
            if (radius <= 0 || faces < 3) return;

            final PoseStack.Pose pose = poseStack.last();
            final Matrix4f matrix = pose.pose();
            final VertexConsumer vertexConsumer = buffer.getBuffer(BALL_RENDER_TYPE);

            final int latBands = faces;
            final int lonBands = faces;
            final float pi = MathUtil.PI;
            final float twoPi = MathUtil.TWO_PI;

            for (int lat = 0; lat < latBands; lat++) {
                float theta1 = pi * (-0.5f + (float) lat / latBands);
                float theta2 = pi * (-0.5f + (float) (lat + 1) / latBands);
                float y1 = radius * (float) Math.sin(theta1);
                float y2 = radius * (float) Math.sin(theta2);
                float scale1 = radius * (float) Math.cos(theta1);
                float scale2 = radius * (float) Math.cos(theta2);

                for (int lon = 0; lon < lonBands; lon++) {
                    float phi1 = twoPi * (float) lon / lonBands;
                    float phi2 = twoPi * (float) (lon + 1) / lonBands;
                    float cosPhi1 = (float) Math.cos(phi1);
                    float sinPhi1 = (float) Math.sin(phi1);
                    float cosPhi2 = (float) Math.cos(phi2);
                    float sinPhi2 = (float) Math.sin(phi2);
                    float x1 = scale1 * cosPhi1;
                    float z1 = scale1 * sinPhi1;
                    float x2 = scale1 * cosPhi2;
                    float z2 = scale1 * sinPhi2;
                    float x3 = scale2 * cosPhi1;
                    float z3 = scale2 * sinPhi1;
                    float x4 = scale2 * cosPhi2;
                    float z4 = scale2 * sinPhi2;

                    vertexConsumer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha).endVertex();
                    vertexConsumer.vertex(matrix, x3, y2, z3).color(red, green, blue, alpha).endVertex();
                    vertexConsumer.vertex(matrix, x2, y1, z2).color(red, green, blue, alpha).endVertex();
                    vertexConsumer.vertex(matrix, x2, y1, z2).color(red, green, blue, alpha).endVertex();
                    vertexConsumer.vertex(matrix, x3, y2, z3).color(red, green, blue, alpha).endVertex();
                    vertexConsumer.vertex(matrix, x4, y2, z4).color(red, green, blue, alpha).endVertex();
                }
            }
        }

        public static void renderBall(PoseStack poseStack, MultiBufferSource buffer, float[][][] vertexBuffer,
                                      float red, float green, float blue, float alpha) {
            if (vertexBuffer == null || vertexBuffer.length == 0) return;
            final PoseStack.Pose pose = poseStack.last();
            final Matrix4f matrix = pose.pose();
            final VertexConsumer vertexConsumer = buffer.getBuffer(BALL_RENDER_TYPE);
            renderBallGeometry(matrix, vertexConsumer, vertexBuffer, red, green, blue, alpha);
        }

        public static void renderBall(Matrix4f matrix, VertexConsumer vertexConsumer, float[][][] vertexBuffer,
                                      float red, float green, float blue, float alpha) {
            renderBallGeometry(matrix, vertexConsumer, vertexBuffer, red, green, blue, alpha);
        }

        private static void renderBallGeometry(Matrix4f matrix, VertexConsumer vertexConsumer,
                                               float[][][] vertexBuffer, float red, float green, float blue, float alpha) {
            for (float[][] floats : vertexBuffer) {
                float x0 = floats[0][0];
                float y0 = floats[0][1];
                float z0 = floats[0][2];
                float x1 = floats[1][0];
                float y1 = floats[1][1];
                float z1 = floats[1][2];
                float x2 = floats[2][0];
                float y2 = floats[2][1];
                float z2 = floats[2][2];

                vertexConsumer.vertex(matrix, x0, y0, z0).color(red, green, blue, alpha).endVertex();
                vertexConsumer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha).endVertex();
                vertexConsumer.vertex(matrix, x2, y2, z2).color(red, green, blue, alpha).endVertex();
            }
        }
    }

    public static final class RayRenderer {
        public static final RenderType RAY_RENDER_TYPE = new RenderType.CompositeRenderType(
                "ray_render_type",
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.TRIANGLE_STRIP,
                128,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStates.POSITION_COLOR_SHADER)
                        .setTransparencyState(RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .setOutputState(ITEM_ENTITY_TARGET)
                        .setCullState(RenderStates.NO_CULL)
                        .createCompositeState(false)
        );

        public static float[][] getRayVertexBuffer(final float yBottom, final float yTop,
                                                   final float radius, final int faces) {
            if (radius <= 0 || faces < 3) return null;

            final int numVertices = (faces + 1) * 2;
            final float[][] vertexBuffer = new float[numVertices][3];
            final double angleStep = MathUtil.TWO_PI / faces;

            for (int i = 0; i <= faces; i++) {
                final double angle = i * angleStep;
                final float x = (float) (radius * Math.cos(angle));
                final float z = (float) (radius * Math.sin(angle));

                int topVertexIndex = i * 2;
                int bottomVertexIndex = i * 2 + 1;

                vertexBuffer[topVertexIndex][0] = x;
                vertexBuffer[topVertexIndex][1] = yTop;
                vertexBuffer[topVertexIndex][2] = z;

                vertexBuffer[bottomVertexIndex][0] = x;
                vertexBuffer[bottomVertexIndex][1] = yBottom;
                vertexBuffer[bottomVertexIndex][2] = z;
            }
            return vertexBuffer;
        }

        public static void renderRay(final PoseStack poseStack, final MultiBufferSource multiBufferSource,
                                     final float red, final float green, float blue, float alpha, final float yBottom, final float yTop, final float radius, final int faces) {
            if (radius <= 0 || faces < 3) return;

            final PoseStack.Pose pose = poseStack.last();
            final Matrix4f matrix4f = pose.pose();
            final VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RAY_RENDER_TYPE);
            final double angleStep = MathUtil.TWO_PI / faces;

            for (int i = 0; i <= faces; i++) {
                final double angle = i * angleStep;
                final float x = (float) (radius * Math.cos(angle));
                final float z = (float) (radius * Math.sin(angle));
                vertexConsumer.vertex(matrix4f, x, yTop, z).color(red, green, blue, alpha).endVertex();
                vertexConsumer.vertex(matrix4f, x, yBottom, z).color(red, green, blue, alpha).endVertex();
            }
        }

        public static void renderRay(PoseStack poseStack, MultiBufferSource buffer, float[][] vertexBuffer,
                                     float red, float green, float blue, float alpha) {
            if (vertexBuffer == null || vertexBuffer.length == 0) return;
            final PoseStack.Pose pose = poseStack.last();
            final Matrix4f matrix = pose.pose();
            final VertexConsumer vertexConsumer = buffer.getBuffer(RAY_RENDER_TYPE);
            renderRayGeometry(matrix, vertexConsumer, vertexBuffer, red, green, blue, alpha);
        }

        public static void renderRay(Matrix4f matrix, VertexConsumer vertexConsumer, float[][] vertexBuffer,
                                     float red, float green, float blue, float alpha) {
            renderRayGeometry(matrix, vertexConsumer, vertexBuffer, red, green, blue, alpha);
        }

        private static void renderRayGeometry(Matrix4f matrix4f, VertexConsumer vertexConsumer, float[][] vertexBuffer,
                                              float red, float green, float blue, float alpha) {
            if (vertexBuffer == null) return;

            for (float[] floats : vertexBuffer) {
                float x = floats[0];
                float y = floats[1];
                float z = floats[2];
                vertexConsumer.vertex(matrix4f, x, y, z).color(red, green, blue, alpha).endVertex();
            }
        }
    }

    public static final class BoxRenderer {
        public static void renderWireframeBox(PoseStack poseStack, MultiBufferSource bufferSource, AABB box,
                                              float r, float g, float b, float a) {
            if (box == null) return;

            final VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
            final PoseStack.Pose pose = poseStack.last();
            final Matrix4f matrix4f = pose.pose();
            final Matrix3f matrix3f = pose.normal();

            float minX = (float) box.minX;
            float minY = (float) box.minY;
            float minZ = (float) box.minZ;
            float maxX = (float) box.maxX;
            float maxY = (float) box.maxY;
            float maxZ = (float) box.maxZ;

            drawLine(vertexConsumer, matrix4f, matrix3f, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
            drawLine(vertexConsumer, matrix4f, matrix3f, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
            drawLine(vertexConsumer, matrix4f, matrix3f, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
            drawLine(vertexConsumer, matrix4f, matrix3f, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

            drawLine(vertexConsumer, matrix4f, matrix3f, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
            drawLine(vertexConsumer, matrix4f, matrix3f, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
            drawLine(vertexConsumer, matrix4f, matrix3f, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
            drawLine(vertexConsumer, matrix4f, matrix3f, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

            drawLine(vertexConsumer, matrix4f, matrix3f, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
            drawLine(vertexConsumer, matrix4f, matrix3f, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
            drawLine(vertexConsumer, matrix4f, matrix3f, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
            drawLine(vertexConsumer, matrix4f, matrix3f, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
        }

        private static void drawLine(VertexConsumer vc, Matrix4f mat, Matrix3f normMat,
                                     float x1, float y1, float z1, float x2, float y2, float z2,
                                     float r, float g, float b, float a) {
            float nx = x2 - x1;
            float ny = y2 - y1;
            float nz = z2 - z1;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1e-6f) {
                nx /= len;
                ny /= len;
                nz /= len;
            } else {
                nx = 0;
                ny = 1;
                nz = 0;
            }
            vc.vertex(mat, x1, y1, z1).color(r, g, b, a).normal(normMat, nx, ny, nz).endVertex();
            vc.vertex(mat, x2, y2, z2).color(r, g, b, a).normal(normMat, nx, ny, nz).endVertex();
        }
    }

    public static final class ArcRenderer {
        public static final ResourceLocation ARC_TEXTURE = new ResourceLocation(AcademyCraft.MOD_ID,
                "textures/ability/electromaster/skill/arc_generate/effect/line_segment.png");
        public static final RenderType ARC_RENDER_TYPE = new RenderType.CompositeRenderType(
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
                        .setShaderState(RenderStates.POSITION_TEX_SHADER)
                        .setCullState(RenderStates.NO_CULL)
                        .setOutputState(ITEM_ENTITY_TARGET)
                        .setTransparencyState(RenderStates.LIGHTNING_TRANSPARENCY)
                        .createCompositeState(false)
        );

        private static final float THICKNESS_VARIATION = 0.4f;
        private static final float MIN_THICKNESS_FACTOR = 0.1f;
        private static final double EPSILON = 1e-6;

        public static void renderArc(PoseStack ps, MultiBufferSource mbs, long seed,
                                     float sx, float sy, float sz, float ex, float ey, float ez,
                                     float thickness, int segments) {
            VertexConsumer vc = mbs.getBuffer(ARC_RENDER_TYPE);
            Matrix4f matrix = ps.last().pose();
            Random rnd = new Random(seed);

            Vec3 start = new Vec3(sx, sy, sz);
            Vec3 end = new Vec3(ex, ey, ez);
            Vec3 delta = end.subtract(start);

            if (delta.lengthSqr() < EPSILON * EPSILON) {
                return;
            }

            Vec3 direction = delta.normalize();

            Vec3 up = new Vec3(0, 1, 0);
            if (Math.abs(direction.y()) > 1.0 - EPSILON) {
                up = new Vec3(1, 0, 0);
            }

            Vec3 side = direction.cross(up);
            if (side.lengthSqr() < EPSILON * EPSILON) {
                up = new Vec3(0, 0, 1);
                side = direction.cross(up);

                if (side.lengthSqr() < EPSILON * EPSILON) {
                    if (direction.lengthSqr() > EPSILON * EPSILON) {
                        Vec3 arbitraryNonParallel = Math.abs(direction.x()) < 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
                        side = direction.cross(arbitraryNonParallel);
                        if (side.lengthSqr() < EPSILON * EPSILON) return;
                    } else {
                        return;
                    }
                }
            }
            side = side.normalize();
            Vec3 renderUp = side.cross(direction).normalize();

            Vec3 prevL = start;
            Vec3 prevR = start;
            float baseHalfThickness = thickness * 0.5f;

            for (int i = 1; i <= segments; ++i) {
                float t = (float) i / segments;
                Vec3 currentMidpoint = start.add(delta.scale(t));

                float displacementMagnitude = baseHalfThickness * MathUtil.TWO_PI;
                float falloff = 1.0f - (float) Math.pow(2.0 * t - 1.0, 2);
                displacementMagnitude *= falloff;
                displacementMagnitude *= (rnd.nextFloat() * 2.0f - 1.0f);

                double angle = rnd.nextDouble() * MathUtil.TWO_PI;
                Vec3 displacementDir = side.scale(Math.cos(angle)).add(renderUp.scale(Math.sin(angle)));

                Vec3 currentPos = currentMidpoint.add(displacementDir.scale(displacementMagnitude));

                float currentHalfThickness = baseHalfThickness;
                currentHalfThickness *= (1.0f + THICKNESS_VARIATION * (rnd.nextFloat() * 2.0f - 1.0f));
                currentHalfThickness = Math.max(baseHalfThickness * MIN_THICKNESS_FACTOR, currentHalfThickness);

                Vec3 currentL = currentPos.subtract(side.scale(currentHalfThickness));
                Vec3 currentR = currentPos.add(side.scale(currentHalfThickness));

                float u0 = (float) (i - 1) / segments;
                float u1 = (float) i / segments;

                vc.vertex(matrix, (float) prevL.x(), (float) prevL.y(), (float) prevL.z()).uv(u0, 0).endVertex();
                vc.vertex(matrix, (float) prevR.x(), (float) prevR.y(), (float) prevR.z()).uv(u0, 1).endVertex();
                vc.vertex(matrix, (float) currentR.x(), (float) currentR.y(), (float) currentR.z()).uv(u1, 1).endVertex();
                vc.vertex(matrix, (float) currentL.x(), (float) currentL.y(), (float) currentL.z()).uv(u1, 0).endVertex();

                prevL = currentL;
                prevR = currentR;
            }
        }
    }

    @SuppressWarnings("deprecation")
    public static final class BakedModelRenderer {
        public static final RenderType BAKED_MODEL_NO_TRANSPARENCY_RENDER_TYPE = new RenderType.CompositeRenderType(
                "baked_model_no_transparency_render_type",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1024,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStates.RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                TextureAtlas.LOCATION_BLOCKS,
                                false,
                                true
                        ))
                        .setLightmapState(RenderStates.LIGHTMAP)
                        .setOverlayState(RenderStates.OVERLAY)
                        .createCompositeState(true)
        );
        public static final RenderType BAKED_MODEL_TRANSPARENCY_RENDER_TYPE = new RenderType.CompositeRenderType(
                "baked_model_transparency_render_type",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1024,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStates.RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                TextureAtlas.LOCATION_BLOCKS,
                                false,
                                true
                        ))
                        .setLightmapState(RenderStates.LIGHTMAP)
                        .setOverlayState(RenderStates.OVERLAY)
                        .setCullState(RenderStates.NO_CULL)
                        .setTransparencyState(RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(true)
        );

        public static void render(PoseStack poseStack, BakedModel bakedModel, MultiBufferSource multiBufferSource, RandomSource randomSource, boolean transparency, int light, int overlay) {
            RenderType renderType = transparency ? BAKED_MODEL_TRANSPARENCY_RENDER_TYPE : BAKED_MODEL_NO_TRANSPARENCY_RENDER_TYPE;
            VertexConsumer vertexConsumer = multiBufferSource.getBuffer(renderType);
            List<BakedQuad> bakedQuadList = bakedModel.getQuads(null, null, randomSource);

            PoseStack.Pose pose = poseStack.last();
            for (BakedQuad bakedQuad : bakedQuadList) {
                vertexConsumer.putBulkData(pose, bakedQuad, 1f, 1f, 1f, light, overlay);
            }
        }
    }
}