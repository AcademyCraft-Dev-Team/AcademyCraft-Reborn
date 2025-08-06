package org.academy.api.client.util;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.*;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.academy.api.client.render.MatrixStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.function.Supplier;

import static net.minecraft.client.renderer.RenderStateShard.POSITION_TEX_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.TRANSLUCENT_TRANSPARENCY;
import static org.academy.api.client.util.RenderStateUtil.POSITION_COLOR_TEX_SHADER_FULL;

/**
 * A utility class for common rendering operations within the UI framework.
 * All drawing methods in this class operate in the local coordinate space of the current
 * MatrixStack. The caller is responsible for translating the stack to the desired
 * widget position before calling these methods.
 */
public final class RenderUtil {
    public static final Supplier<Boolean> IS_SHADER_PACK_IN_USE;

    static {
        Supplier<Boolean> result;
        try {
            IrisApi.getInstance().isShaderPackInUse();
            result = () -> IrisApi.getInstance().isShaderPackInUse();
        } catch (Throwable e) {
            result = () -> false;
        }
        IS_SHADER_PACK_IN_USE = result;
    }

    private RenderUtil() {
    }

    /**
     * Fills a rectangular area in the current local coordinate space.
     * Assumes the MatrixStack has already been translated to the widget's position.
     */
    public static void fill(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, float x, float y, float width, float height, int color) {
        Matrix4f matrix = stack.lastMatrix();
        float x2 = x + width;
        float y2 = y + height;

        float a = (float) FastColor.ARGB32.alpha(color) / 255.0F;
        float r = (float) FastColor.ARGB32.red(color) / 255.0F;
        float g = (float) FastColor.ARGB32.green(color) / 255.0F;
        float b = (float) FastColor.ARGB32.blue(color) / 255.0F;

        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.gui());
        vertexConsumer.addVertex(matrix, x, y2, 0.0F).setColor(r, g, b, a);
        vertexConsumer.addVertex(matrix, x2, y2, 0.0F).setColor(r, g, b, a);
        vertexConsumer.addVertex(matrix, x2, y, 0.0F).setColor(r, g, b, a);
        vertexConsumer.addVertex(matrix, x, y, 0.0F).setColor(r, g, b, a);
    }

    /**
     * Draws an outline of a rectangle in the current local coordinate space.
     * Assumes the MatrixStack has already been translated to the widget's position.
     */
    public static void drawOutline(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, float x, float y, float width, float height, int color, float lineWidth) {
        float x2 = x + width;
        float y2 = y + height;
        fill(stack, bufferSource, x, y, width, lineWidth, color);
        fill(stack, bufferSource, x, y2 - lineWidth, width, lineWidth, color);
        fill(stack, bufferSource, x, y, lineWidth, height, color);
        fill(stack, bufferSource, x2 - lineWidth, y, lineWidth, height, color);
    }

    /**
     * Draws a textured quad of a given size in the current local coordinate space.
     * Assumes the MatrixStack has already been translated to the widget's position.
     */
    public static void blitWithRenderType(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, RenderType renderType, float x, float y, float width, float height, float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        Matrix4f matrix = stack.lastMatrix();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);

        float x2 = x + width;
        float y2 = y + height;

        vertexConsumer.addVertex(matrix, x, y2, 0).setColor(r, g, b, a).setUv(u0, v1);
        vertexConsumer.addVertex(matrix, x2, y2, 0).setColor(r, g, b, a).setUv(u1, v1);
        vertexConsumer.addVertex(matrix, x2, y, 0).setColor(r, g, b, a).setUv(u1, v0);
        vertexConsumer.addVertex(matrix, x, y, 0).setColor(r, g, b, a).setUv(u0, v0);
    }

    /**
     * Draws a string at (0,0) in the current local coordinate space.
     * Assumes the MatrixStack has already been translated to the desired text position.
     */
    public static int drawString(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, Font font, String text, int color, boolean hasShadow) {
        if (text == null) return 0;

        return font.drawInBatch(text, 0, 0, color, hasShadow, stack.lastMatrix(), bufferSource, Font.DisplayMode.NORMAL, 0, 15728880);
    }

    /**
     * Draws a Component at (0,0) in the current local coordinate space.
     * Assumes the MatrixStack has already been translated to the desired text position.
     */
    public static int drawString(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, Font font, Component component, int color, boolean hasShadow) {
        if (component == null) return 0;

        return font.drawInBatch(component, 0, 0, color, hasShadow, stack.lastMatrix(), bufferSource, Font.DisplayMode.NORMAL, 0, 15728880);
    }

    /**
     * Applies a multiplicative alpha factor to a packed ARGB color.
     */
    public static int applyAlpha(int color, float alphaFactor) {
        int baseAlpha = FastColor.ARGB32.alpha(color);
        int finalAlpha = (int) (baseAlpha * alphaFactor);
        return (color & 0x00FFFFFF) | (finalAlpha << 24);
    }

    @NotNull
    public static RenderType getPositionTexRenderType(@NotNull String name, @NotNull ResourceLocation resourceLocation, boolean blur) {
        return RenderType.create(
                name,
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                256,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_TEX_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, blur, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false));
    }

    @NotNull
    public static RenderType getPositionColorTexRenderType(@NotNull String name, @NotNull ResourceLocation resourceLocation, boolean blur) {
        return RenderType.create(
                name,
                VertexFormat.builder()
                        .add("Position", VertexFormatElement.POSITION)
                        .add("Color", VertexFormatElement.COLOR)
                        .add("UV0", VertexFormatElement.UV0)
                        .build(),
                VertexFormat.Mode.QUADS,
                256,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_COLOR_TEX_SHADER_FULL)
                        .setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, blur, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false));
    }

    /**
     * A highly specialized utility for fullscreen post-processing effects.
     * This does not participate in the standard widget rendering pipeline.
     */
    public static void blitScreen(ShaderInstance shaderInstance, RenderTarget dist) {
        dist.clear(Minecraft.ON_OSX);
        dist.bindWrite(false);
        shaderInstance.apply();
        var builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.addVertex(-1, 1, 0);
        builder.addVertex(-1, -1, 0);
        builder.addVertex(1, -1, 0);
        builder.addVertex(1, 1, 0);
        BufferUploader.draw(builder.buildOrThrow());
        shaderInstance.clear();
    }
}