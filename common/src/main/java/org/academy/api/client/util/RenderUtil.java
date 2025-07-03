package org.academy.api.client.util;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.util.MathUtil;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;

import static net.minecraft.client.renderer.RenderStateShard.ITEM_ENTITY_TARGET;
import static net.minecraft.client.renderer.RenderStateShard.TextureStateShard;
import static org.academy.api.client.util.RenderStateUtil.*;

@SuppressWarnings("DuplicatedCode")
public final class RenderUtil {
    public static final Supplier<Boolean> IS_SHADER_PACK_IN_USE;

    static {
        Supplier<Boolean> result;
        try {
            var irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");

            var getInstanceMethod = irisApiClass.getDeclaredMethod("getInstance");
            getInstanceMethod.setAccessible(true);

            var isShaderPackInUseMethod = irisApiClass.getMethod("isShaderPackInUse");
            isShaderPackInUseMethod.setAccessible(true);

            result = () -> {
                try {
                    var instance = getInstanceMethod.invoke(null);
                    return (Boolean) isShaderPackInUseMethod.invoke(instance);
                } catch (Exception e) {
                    return false;
                }
            };
        } catch (Exception e) {
            result = () -> false;
        }
        IS_SHADER_PACK_IN_USE = result;
    }

    private RenderUtil() {
    }

    @NotNull
    public static RenderType getPositionTexRenderType(@NotNull String name, @NotNull ResourceLocation resourceLocation, boolean blur) {
        return new RenderType.CompositeRenderType(
                name,
                DefaultVertexFormat.POSITION_TEX,
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
                        .setShaderState(POSITION_TEX_SHADER)
                        .setCullState(NO_CULL)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false));
    }

    @NotNull
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
                        .setShaderState(POSITION_COLOR_TEX_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false));
    }

    @NotNull
    public static RenderType getPositionColorTexRenderTypeFull(@NotNull String name, @NotNull ResourceLocation resourceLocation, boolean blur) {
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
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setShaderState(POSITION_COLOR_TEX_SHADER_FULL)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false));
    }

    public static void fill(Matrix4f matrix4f, float minX, float minY, float maxX, float maxY, int color, MultiBufferSource buffer) {
        if (minX < maxX) {
            var i = minX;
            minX = maxX;
            maxX = i;
        }

        if (minY < maxY) {
            var j = minY;
            minY = maxY;
            maxY = j;
        }

        var f3 = (float) FastColor.ARGB32.alpha(color) / 255.0F;
        var f = (float) FastColor.ARGB32.red(color) / 255.0F;
        var f1 = (float) FastColor.ARGB32.green(color) / 255.0F;
        var f2 = (float) FastColor.ARGB32.blue(color) / 255.0F;
        VertexConsumer vertexconsumer = buffer.getBuffer(RenderType.gui());
        vertexconsumer.vertex(matrix4f, minX, minY, 0).color(f, f1, f2, f3).endVertex();
        vertexconsumer.vertex(matrix4f, minX, maxY, 0).color(f, f1, f2, f3).endVertex();
        vertexconsumer.vertex(matrix4f, maxX, maxY, 0).color(f, f1, f2, f3).endVertex();
        vertexconsumer.vertex(matrix4f, maxX, minY, 0).color(f, f1, f2, f3).endVertex();
    }

    public static final class RingRenderer {
        public static final Function<ResourceLocation, RenderType> RING_RENDER_TYPE = resourceLocation
                -> new RenderType.CompositeRenderType(
                "ring_render_type",
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                512,
                true,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(new TextureStateShard(resourceLocation, false, false))
                        .setShaderState(POSITION_TEX_SHADER)
                        .setCullState(NO_CULL)
                        .setOutputState(ITEM_ENTITY_TARGET)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );

        public static void renderRing(Matrix4f matrix, VertexConsumer vertexConsumer,
                                      int segments, float[][][] vertexBuffer) {
            for (var i = 0; i < segments; i++) {
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
    }

    public static final class CylinderRenderer {
        public static final RenderType CYLINDER_RENDER_TYPE = new RenderType.CompositeRenderType(
                "cylinder_render_type",
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.TRIANGLE_STRIP,
                128,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setOutputState(ITEM_ENTITY_TARGET)
                        .setCullState(NO_CULL)
                        .createCompositeState(false)
        );

        public static void renderCylinder(final PoseStack poseStack, final MultiBufferSource multiBufferSource,
                                          final float red, final float green, float blue, float alpha, final float yBottom, final float yTop, final float radius, final int faces) {
            final var pose = poseStack.last();
            final var matrix4f = pose.pose();
            final var vertexConsumer = multiBufferSource.getBuffer(CYLINDER_RENDER_TYPE);
            final var angleStep = MathUtil.TWO_PI / faces;

            for (var i = 0; i <= faces; i++) {
                final var angle = i * angleStep;
                final var x = (float) (radius * Math.cos(angle));
                final var z = (float) (radius * Math.sin(angle));
                vertexConsumer.vertex(matrix4f, x, yTop, z).color(red, green, blue, alpha).endVertex();
                vertexConsumer.vertex(matrix4f, x, yBottom, z).color(red, green, blue, alpha).endVertex();
            }
        }

        public static void renderCylinder(PoseStack poseStack, MultiBufferSource buffer, float[][] vertexBuffer,
                                          float red, float green, float blue, float alpha) {
            final var pose = poseStack.last();
            final var matrix = pose.pose();
            final var vertexConsumer = buffer.getBuffer(CYLINDER_RENDER_TYPE);
            renderCylinder(matrix, vertexConsumer, vertexBuffer, red, green, blue, alpha);
        }

        public static void renderCylinder(Matrix4f matrix4f, VertexConsumer vertexConsumer, float[][] vertexBuffer,
                                          float red, float green, float blue, float alpha) {
            for (var floats : vertexBuffer) {
                var x = floats[0];
                var y = floats[1];
                var z = floats[2];
                vertexConsumer.vertex(matrix4f, x, y, z).color(red, green, blue, alpha).endVertex();
            }
        }
    }

    public static final class LineBoxRenderer {
        public static void renderWireframeBox(PoseStack poseStack, MultiBufferSource bufferSource, AABB box,
                                              float r, float g, float b, float a) {
            final var vertexConsumer = bufferSource.getBuffer(RenderType.lines());
            final var pose = poseStack.last();
            final var matrix4f = pose.pose();
            final var matrix3f = pose.normal();

            var minX = (float) box.minX;
            var minY = (float) box.minY;
            var minZ = (float) box.minZ;
            var maxX = (float) box.maxX;
            var maxY = (float) box.maxY;
            var maxZ = (float) box.maxZ;

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
            var nx = x2 - x1;
            var ny = y2 - y1;
            var nz = z2 - z1;
            var len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
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

    public static final class BoxRenderer {
        public static final RenderType FILLED_BOX_RENDER_TYPE = new RenderType.CompositeRenderType(
                "filled_box_render_type",
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS,
                256,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOutputState(ITEM_ENTITY_TARGET)
                        .createCompositeState(false)
        );

        public static void renderFilledBox(PoseStack poseStack, MultiBufferSource bufferSource, AABB box,
                                           float r, float g, float b, float a) {
            final var vertexConsumer = bufferSource.getBuffer(FILLED_BOX_RENDER_TYPE);
            final var matrix4f = poseStack.last().pose();

            var faces = VertexUtil.Box.getBoxVertices(box);

            for (var face : faces) {
                vertexConsumer.vertex(matrix4f, face[0][0], face[0][1], face[0][2]).color(r, g, b, a).endVertex();
                vertexConsumer.vertex(matrix4f, face[1][0], face[1][1], face[1][2]).color(r, g, b, a).endVertex();
                vertexConsumer.vertex(matrix4f, face[2][0], face[2][1], face[2][2]).color(r, g, b, a).endVertex();
                vertexConsumer.vertex(matrix4f, face[3][0], face[3][1], face[3][2]).color(r, g, b, a).endVertex();
            }
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
                        .setShaderState(POSITION_TEX_SHADER)
                        .setCullState(NO_CULL)
                        .setOutputState(ITEM_ENTITY_TARGET)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .createCompositeState(false)
        );

        private static final float THICKNESS_VARIATION = 0.4f;
        private static final float MIN_THICKNESS_FACTOR = 0.1f;
        private static final double EPSILON = 1e-6;

        public static void renderArc(PoseStack ps, MultiBufferSource mbs, long seed,
                                     float sx, float sy, float sz, float ex, float ey, float ez,
                                     float thickness, int segments) {
            var vc = mbs.getBuffer(ARC_RENDER_TYPE);
            var matrix = ps.last().pose();
            var rnd = new Random(seed);

            var start = new Vec3(sx, sy, sz);
            var end = new Vec3(ex, ey, ez);
            var delta = end.subtract(start);

            if (delta.lengthSqr() < EPSILON * EPSILON) {
                return;
            }

            var direction = delta.normalize();

            var up = new Vec3(0, 1, 0);
            if (Math.abs(direction.y()) > 1.0 - EPSILON) {
                up = new Vec3(1, 0, 0);
            }

            var side = direction.cross(up);
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
            var renderUp = side.cross(direction).normalize();

            var prevL = start;
            var prevR = start;
            float baseHalfThickness = thickness * 0.5f;

            for (int i = 1; i <= segments; ++i) {
                var t = (float) i / segments;
                var currentMidpoint = start.add(delta.scale(t));

                var displacementMagnitude = baseHalfThickness * MathUtil.TWO_PI;
                var falloff = 1.0f - (float) Math.pow(2.0 * t - 1.0, 2);
                displacementMagnitude *= falloff;
                displacementMagnitude *= (rnd.nextFloat() * 2.0f - 1.0f);

                var angle = rnd.nextDouble() * MathUtil.TWO_PI;
                var displacementDir = side.scale(Math.cos(angle)).add(renderUp.scale(Math.sin(angle)));

                var currentPos = currentMidpoint.add(displacementDir.scale(displacementMagnitude));

                var currentHalfThickness = baseHalfThickness;
                currentHalfThickness *= (1.0f + THICKNESS_VARIATION * (rnd.nextFloat() * 2.0f - 1.0f));
                currentHalfThickness = Math.max(baseHalfThickness * MIN_THICKNESS_FACTOR, currentHalfThickness);

                var currentL = currentPos.subtract(side.scale(currentHalfThickness));
                var currentR = currentPos.add(side.scale(currentHalfThickness));

                var u0 = (float) (i - 1) / segments;
                var u1 = (float) i / segments;

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
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                TextureAtlas.LOCATION_BLOCKS,
                                false,
                                true
                        ))
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
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
            var renderType = transparency ? BAKED_MODEL_TRANSPARENCY_RENDER_TYPE : BAKED_MODEL_NO_TRANSPARENCY_RENDER_TYPE;
            var vertexConsumer = multiBufferSource.getBuffer(renderType);
            var bakedQuadList = bakedModel.getQuads(null, null, randomSource);

            var pose = poseStack.last();
            for (var bakedQuad : bakedQuadList) {
                vertexConsumer.putBulkData(pose, bakedQuad, 1f, 1f, 1f, light, overlay);
            }
        }
    }
}