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
        var vertexconsumer = buffer.getBuffer(RenderType.gui());
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
                var v0 = vertexBuffer[i][0];
                var v1 = vertexBuffer[i][1];
                var v2 = vertexBuffer[i][2];
                var v3 = vertexBuffer[i][3];

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
            var rnd = new Random(seed);
            renderArcRecursive(ps, mbs, rnd, sx, sy, sz, ex, ey, ez, thickness, segments, 0.15f, 0);
        }

        private static void renderArcRecursive(PoseStack ps, MultiBufferSource mbs, Random rnd,
                                               float startX, float startY, float startZ,
                                               float endX, float endY, float endZ,
                                               float thickness, int segments,
                                               float branchChance, int depth) {
            var distSq = (endX - startX) * (endX - startX) + (endY - startY) * (endY - startY) + (endZ - startZ) * (endZ - startZ);
            if (depth > 1 || distSq < EPSILON * EPSILON) {
                return;
            }

            var vc = mbs.getBuffer(ARC_RENDER_TYPE);
            var matrix = ps.last().pose();

            var deltaX = endX - startX;
            var deltaY = endY - startY;
            var deltaZ = endZ - startZ;

            var prevPosX = startX;
            var prevPosY = startY;
            var prevPosZ = startZ;
            float prevLX = 0, prevLY = 0, prevLZ = 0, prevRX = 0, prevRY = 0, prevRZ = 0;
            var baseHalfThickness = thickness * 0.5f;

            for (var i = 1; i <= segments; ++i) {
                var t = (float) i / segments;
                var currentMidpointX = startX + deltaX * t;
                var currentMidpointY = startY + deltaY * t;
                var currentMidpointZ = startZ + deltaZ * t;

                float displacementDirX, displacementDirY, displacementDirZ;
                var deltaLenSq = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
                if (deltaLenSq > EPSILON) {
                    var invDeltaLen = 1.0f / (float) Math.sqrt(deltaLenSq);
                    var dirX = deltaX * invDeltaLen;
                    var dirY = deltaY * invDeltaLen;
                    var dirZ = deltaZ * invDeltaLen;

                    var upX = 0.0f;
                    var upY = 1.0f;
                    if (Math.abs(dirY) > 1.0f - EPSILON) {
                        upX = 1.0f;
                        upY = 0.0f;
                    }

                    var sideX = dirY * 0 - dirZ * upY;
                    var sideY = dirZ * upX - dirX * 0;
                    var sideZ = dirX * upY - dirY * upX;
                    var invSideLen = 1.0f / (float) Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
                    sideX *= invSideLen;
                    sideY *= invSideLen;
                    sideZ *= invSideLen;

                    var renderUpX = sideY * dirZ - sideZ * dirY;
                    var renderUpY = sideZ * dirX - sideX * dirZ;
                    var renderUpZ = sideX * dirY - sideY * dirX;

                    var angle = rnd.nextFloat() * MathUtil.TWO_PI;
                    var cosAngle = (float) Math.cos(angle);
                    var sinAngle = (float) Math.sin(angle);
                    displacementDirX = sideX * cosAngle + renderUpX * sinAngle;
                    displacementDirY = sideY * cosAngle + renderUpY * sinAngle;
                    displacementDirZ = sideZ * cosAngle + renderUpZ * sinAngle;
                } else {
                    displacementDirX = rnd.nextFloat() - 0.5f;
                    displacementDirY = rnd.nextFloat() - 0.5f;
                    displacementDirZ = rnd.nextFloat() - 0.5f;
                }

                var falloff = 1.0f - (float) Math.pow(2.0 * t - 1.0, 2);
                var displacementMagnitude = baseHalfThickness * 5.0f * falloff * (rnd.nextFloat() * 0.8f + 0.2f);
                var currentPosX = currentMidpointX + displacementDirX * displacementMagnitude;
                var currentPosY = currentMidpointY + displacementDirY * displacementMagnitude;
                var currentPosZ = currentMidpointZ + displacementDirZ * displacementMagnitude;

                var segDeltaX = currentPosX - prevPosX;
                var segDeltaY = currentPosY - prevPosY;
                var segDeltaZ = currentPosZ - prevPosZ;

                var invSegLen = 1.0f / (float) Math.sqrt(segDeltaX * segDeltaX + segDeltaY * segDeltaY + segDeltaZ * segDeltaZ);
                var segDirX = segDeltaX * invSegLen;
                var segDirY = segDeltaY * invSegLen;
                var segDirZ = segDeltaZ * invSegLen;

                var segUpX = 0.0f;
                var segUpY = 1.0f;
                if (Math.abs(segDirY) > 1.0f - EPSILON) {
                    segUpX = 1.0f;
                    segUpY = 0.0f;
                }

                var segSideX = segDirY * 0 - segDirZ * segUpY;
                var segSideY = segDirZ * segUpX - segDirX * 0;
                var segSideZ = segDirX * segUpY - segDirY * segUpX;
                var invSegSideLen = 1.0f / (float) Math.sqrt(segSideX * segSideX + segSideY * segSideY + segSideZ * segSideZ);
                segSideX *= invSegSideLen;
                segSideY *= invSegSideLen;
                segSideZ *= invSegSideLen;

                var currentHalfThickness = baseHalfThickness * (1.0f + THICKNESS_VARIATION * (rnd.nextFloat() * 2.0f - 1.0f));
                currentHalfThickness = Math.max(baseHalfThickness * MIN_THICKNESS_FACTOR, currentHalfThickness);

                var currentLX = currentPosX - segSideX * currentHalfThickness;
                var currentLY = currentPosY - segSideY * currentHalfThickness;
                var currentLZ = currentPosZ - segSideZ * currentHalfThickness;
                var currentRX = currentPosX + segSideX * currentHalfThickness;
                var currentRY = currentPosY + segSideY * currentHalfThickness;
                var currentRZ = currentPosZ + segSideZ * currentHalfThickness;

                if (i == 1) {
                    prevLX = prevPosX - segSideX * currentHalfThickness;
                    prevLY = prevPosY - segSideY * currentHalfThickness;
                    prevLZ = prevPosZ - segSideZ * currentHalfThickness;
                    prevRX = prevPosX + segSideX * currentHalfThickness;
                    prevRY = prevPosY + segSideY * currentHalfThickness;
                    prevRZ = prevPosZ + segSideZ * currentHalfThickness;
                }

                var u0 = (float) (i - 1) / segments;
                var u1 = (float) i / segments;

                vc.vertex(matrix, prevLX, prevLY, prevLZ).uv(u0, 0).endVertex();
                vc.vertex(matrix, prevRX, prevRY, prevRZ).uv(u0, 1).endVertex();
                vc.vertex(matrix, currentRX, currentRY, currentRZ).uv(u1, 1).endVertex();
                vc.vertex(matrix, currentLX, currentLY, currentLZ).uv(u1, 0).endVertex();

                if (depth == 0 && i > 1 && i < segments - 1 && rnd.nextFloat() < branchChance) {
                    var branchLength = (float) Math.sqrt((endX - currentPosX) * (endX - currentPosX) + (endY - currentPosY) * (endY - currentPosY) + (endZ - currentPosZ) * (endZ - currentPosZ)) * (0.3f + rnd.nextFloat() * 0.4f);
                    var angleOffset = (rnd.nextFloat() - 0.5f) * Math.PI * 1.2f;

                    var cosY = (float) Math.cos(angleOffset);
                    var sinY = (float) Math.sin(angleOffset);
                    var branchDirX = segDirX * cosY - segDirZ * sinY;
                    var branchDirZ = segDirX * sinY + segDirZ * cosY;

                    var branchEndX = currentPosX + branchDirX * branchLength;
                    var branchEndY = currentPosY + segDirY * branchLength;
                    var branchEndZ = currentPosZ + branchDirZ * branchLength;

                    renderArcRecursive(ps, mbs, rnd, currentPosX, currentPosY, currentPosZ, branchEndX, branchEndY, branchEndZ, thickness * 0.6f, (int) (segments * 0.6f), 0, depth + 1);
                }

                prevPosX = currentPosX;
                prevPosY = currentPosY;
                prevPosZ = currentPosZ;
                prevLX = currentLX;
                prevLY = currentLY;
                prevLZ = currentLZ;
                prevRX = currentRX;
                prevRY = currentRY;
                prevRZ = currentRZ;
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