package org.academy.api.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.academy.api.client.render.MatrixStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.function.Supplier;

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

    public static void fill(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, float x1, float y1, float x2, float y2, int color) {
        Matrix4f matrix = stack.lastMatrix();

        float j;
        if (x1 < x2) {
            j = x1;
            x1 = x2;
            x2 = j;
        }

        if (y1 < y2) {
            j = y1;
            y1 = y2;
            y2 = j;
        }

        float f3 = (float) FastColor.ARGB32.alpha(color) / 255.0F;
        float f = (float) FastColor.ARGB32.red(color) / 255.0F;
        float f1 = (float) FastColor.ARGB32.green(color) / 255.0F;
        float f2 = (float) FastColor.ARGB32.blue(color) / 255.0F;
        VertexConsumer vertexconsumer = bufferSource.getBuffer(RenderType.gui());
        vertexconsumer.vertex(matrix, x1, y1, 0.0F).color(f, f1, f2, f3).endVertex();
        vertexconsumer.vertex(matrix, x1, y2, 0.0F).color(f, f1, f2, f3).endVertex();
        vertexconsumer.vertex(matrix, x2, y2, 0.0F).color(f, f1, f2, f3).endVertex();
        vertexconsumer.vertex(matrix, x2, y1, 0.0F).color(f, f1, f2, f3).endVertex();
    }

    public static void drawOutline(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, float x, float y, float width, float height, int color, float lineWidth) {
        float x2 = x + width;
        float y2 = y + height;
        fill(stack, bufferSource, x, y, x2, y + lineWidth, color);
        fill(stack, bufferSource, x, y2 - lineWidth, x2, y2, color);
        fill(stack, bufferSource, x, y + lineWidth, x + lineWidth, y2 - lineWidth, color);
        fill(stack, bufferSource, x2 - lineWidth, y + lineWidth, x2, y2 - lineWidth, color);
    }

    public static void blitWithRenderType(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, RenderType renderType, float x, float y, float width, float height, float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        Matrix4f matrix = stack.lastMatrix();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);

        float x2 = x + width;
        float y2 = y + height;

        vertexConsumer.vertex(matrix, x, y, 0).color(r, g, b, a).uv(u0, v0).endVertex();
        vertexConsumer.vertex(matrix, x, y2, 0).color(r, g, b, a).uv(u0, v1).endVertex();
        vertexConsumer.vertex(matrix, x2, y2, 0).color(r, g, b, a).uv(u1, v1).endVertex();
        vertexConsumer.vertex(matrix, x2, y, 0).color(r, g, b, a).uv(u1, v0).endVertex();
    }

    public static void blit(MatrixStack stack, ResourceLocation atlasLocation, float x, float y, float width, float height, float u0, float v0, float u1, float v1) {
        blit(stack, atlasLocation, x, y, width, height, u0, v0, u1, v1, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void blit(MatrixStack stack, ResourceLocation atlasLocation, float x, float y, float width, float height, float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        innerBlit(stack, atlasLocation, x, x + width, y, y + height, 0, u0, u1, v0, v1, r, g, b, a);
    }

    private static void innerBlit(MatrixStack stack, ResourceLocation atlasLocation, float x1, float x2, float y1, float y2, int blitOffset, float minU, float maxU, float minV, float maxV, float r, float g, float b, float a) {
        RenderSystem.setShaderTexture(0, atlasLocation);
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.enableBlend();
        Matrix4f matrix = stack.lastMatrix();
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);

        bufferBuilder.vertex(matrix, x1, y1, blitOffset).color(r, g, b, a).uv(minU, minV).endVertex();
        bufferBuilder.vertex(matrix, x1, y2, blitOffset).color(r, g, b, a).uv(minU, maxV).endVertex();
        bufferBuilder.vertex(matrix, x2, y2, blitOffset).color(r, g, b, a).uv(maxU, maxV).endVertex();
        bufferBuilder.vertex(matrix, x2, y1, blitOffset).color(r, g, b, a).uv(maxU, minV).endVertex();

        BufferUploader.drawWithShader(bufferBuilder.end());
        RenderSystem.disableBlend();
    }

    public static int drawString(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, Font font, String text, float x, float y, int color, boolean hasShadow) {
        if (text == null) {
            return 0;
        }
        if (hasShadow) {
            return font.drawInBatch(text, x, y, color, true, stack.lastMatrix(), bufferSource, Font.DisplayMode.NORMAL, 0, 15728880);
        } else {
            return font.drawInBatch(text, x, y, color, false, stack.lastMatrix(), bufferSource, Font.DisplayMode.NORMAL, 0, 15728880);
        }
    }

    public static int drawString(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, Font font, Component component, float x, float y, int color, boolean hasShadow) {
        if (component == null) {
            return 0;
        }
        if (hasShadow) {
            return font.drawInBatch(component, x, y, color, true, stack.lastMatrix(), bufferSource, Font.DisplayMode.NORMAL, 0, 15728880);
        } else {
            return font.drawInBatch(component, x, y, color, false, stack.lastMatrix(), bufferSource, Font.DisplayMode.NORMAL, 0, 15728880);
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
}