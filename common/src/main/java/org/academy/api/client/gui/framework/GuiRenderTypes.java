package org.academy.api.client.gui.framework;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

public class GuiRenderTypes extends RenderStateShard {
    private static final Map<ResourceLocation, RenderType> TEXTURED_QUAD_CACHE = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> TEXTURED_QUAD_NO_MIPMAP_CACHE = new HashMap<>();

    public static final RenderType COLORED_QUAD = new RenderType.CompositeRenderType("gui_colored_quad",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true, // Increased buffer size
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY) // Enable alpha blending
                    .createCompositeState(false)); // 'outline' parameter likely false for GUI

    public static RenderType getTexturedQuad(ResourceLocation texture) {
        return TEXTURED_QUAD_CACHE.computeIfAbsent(texture, loc ->
                new RenderType.CompositeRenderType("gui_textured_quad_" + loc.toString().hashCode(),
                        DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 256, true, true, // Mipmap enabled
                        RenderType.CompositeState.builder()
                                .setShaderState(RenderStateShard.POSITION_TEX_SHADER)
                                .setTextureState(new RenderStateShard.TextureStateShard(loc, true, false)) // Blur true, Mipmap false? Check usage. Let's try Blur=true, Mipmap=true.
                                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                                .createCompositeState(true))); // Outline true? Test this. Let's assume true like vanilla widgets.
    }

    public static RenderType getTexturedQuadNoMipmap(ResourceLocation texture) {
        return TEXTURED_QUAD_NO_MIPMAP_CACHE.computeIfAbsent(texture, loc ->
                new RenderType.CompositeRenderType("gui_textured_quad_no_mipmap_" + loc.toString().hashCode(),
                        DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 256, false, true, // Mipmap disabled
                        RenderType.CompositeState.builder()
                                .setShaderState(RenderStateShard.POSITION_TEX_SHADER)
                                .setTextureState(new RenderStateShard.TextureStateShard(loc, false, false)) // Blur false, Mipmap false
                                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                                .createCompositeState(false)));
    }

    /**
     * Draws a nine-sliced textured quad using a VertexConsumer.
     * Mimics GuiComponent.blitNineSliced functionality.
     *
     * @param consumer      The VertexConsumer for the appropriate textured RenderType.
     * @param poseStack     The current PoseStack.
     * @param x             Screen X coordinate.
     * @param y             Screen Y coordinate.
     * @param width         Total width of the rendered element.
     * @param height        Total height of the rendered element.
     * @param u             Texture U coordinate of the top-left corner of the 4x4 grid.
     * @param v             Texture V coordinate of the top-left corner of the 4x4 grid.
     * @param sliceWidth    Width of a single slice/corner in the texture.
     * @param sliceHeight   Height of a single slice/corner in the texture.
     * @param textureWidth  Total width of the texture file.
     * @param textureHeight Total height of the texture file.
     * @param cornerSize    Size of the corner sections (how much of sliceWidth/Height is the corner).
     */
    public static void drawNineSlicedQuad(VertexConsumer consumer, PoseStack poseStack,
                                          float x, float y, float width, float height,
                                          float u, float v, int sliceWidth, int sliceHeight,
                                          int textureWidth, int textureHeight, int cornerSize) {

        float texU = u / (float) textureWidth;
        float texV = v / (float) textureHeight;
        float sliceTexWidth = (float) sliceWidth / (float) textureWidth;
        float sliceTexHeight = (float) sliceHeight / (float) textureHeight;
        float cornerTexSizeW = (float) cornerSize / (float) textureWidth;
        float cornerTexSizeH = (float) cornerSize / (float) textureHeight;

        float endX = x + width;
        float endY = y + height;
        float centerWidth = width - cornerSize * 2;
        float centerHeight = height - cornerSize * 2;

        Matrix4f matrix = poseStack.last().pose();
        float z = 0; // Assuming GUI rendering at z=0

        // Top-Left Corner
        drawQuad(consumer, matrix, x, y, cornerSize, cornerSize, z, texU, texV, cornerTexSizeW, cornerTexSizeH);
        // Top-Right Corner
        drawQuad(consumer, matrix, endX - cornerSize, y, cornerSize, cornerSize, z, texU + sliceTexWidth - cornerTexSizeW, texV, cornerTexSizeW, cornerTexSizeH);
        // Bottom-Left Corner
        drawQuad(consumer, matrix, x, endY - cornerSize, cornerSize, cornerSize, z, texU, texV + sliceTexHeight - cornerTexSizeH, cornerTexSizeW, cornerTexSizeH);
        // Bottom-Right Corner
        drawQuad(consumer, matrix, endX - cornerSize, endY - cornerSize, cornerSize, cornerSize, z, texU + sliceTexWidth - cornerTexSizeW, texV + sliceTexHeight - cornerTexSizeH, cornerTexSizeW, cornerTexSizeH);

        if (centerWidth > 0) {
            // Top Edge
            drawQuad(consumer, matrix, x + cornerSize, y, centerWidth, cornerSize, z, texU + cornerTexSizeW, texV, sliceTexWidth - cornerTexSizeW * 2, cornerTexSizeH);
            // Bottom Edge
            drawQuad(consumer, matrix, x + cornerSize, endY - cornerSize, centerWidth, cornerSize, z, texU + cornerTexSizeW, texV + sliceTexHeight - cornerTexSizeH, sliceTexWidth - cornerTexSizeW * 2, cornerTexSizeH);
        }

        if (centerHeight > 0) {
            // Left Edge
            drawQuad(consumer, matrix, x, y + cornerSize, cornerSize, centerHeight, z, texU, texV + cornerTexSizeH, cornerTexSizeW, sliceTexHeight - cornerTexSizeH * 2);
            // Right Edge
            drawQuad(consumer, matrix, endX - cornerSize, y + cornerSize, cornerSize, centerHeight, z, texU + sliceTexWidth - cornerTexSizeW, texV + cornerTexSizeH, cornerTexSizeW, sliceTexHeight - cornerTexSizeH * 2);
        }

        if (centerWidth > 0 && centerHeight > 0) {
            // Center
            drawQuad(consumer, matrix, x + cornerSize, y + cornerSize, centerWidth, centerHeight, z, texU + cornerTexSizeW, texV + cornerTexSizeH, sliceTexWidth - cornerTexSizeW * 2, sliceTexHeight - cornerTexSizeH * 2);
        }
    }

    // --- Helper to draw a simple textured quad ---
    public static void drawQuad(VertexConsumer consumer, Matrix4f matrix, float x, float y, float w, float h, float z, float u0, float v0, float uWidth, float vHeight) {
        float x2 = x + w;
        float y2 = y + h;
        float u1 = u0 + uWidth;
        float v1 = v0 + vHeight;
        consumer.vertex(matrix, x, y, z).uv(u0, v0).endVertex();
        consumer.vertex(matrix, x, y2, z).uv(u0, v1).endVertex();
        consumer.vertex(matrix, x2, y2, z).uv(u1, v1).endVertex();
        consumer.vertex(matrix, x2, y, z).uv(u1, v0).endVertex();
    }

    // --- Helper to draw a simple colored quad ---
    public static void drawColoredQuad(VertexConsumer consumer, Matrix4f matrix, float x, float y, float w, float h, float z, int color) {
        float x2 = x + w;
        float y2 = y + h;
        float alpha = (float)(color >> 24 & 255) / 255.0F;
        float red = (float)(color >> 16 & 255) / 255.0F;
        float green = (float)(color >> 8 & 255) / 255.0F;
        float blue = (float)(color & 255) / 255.0F;
        consumer.vertex(matrix, x, y, z).color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, x, y2, z).color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, x2, y2, z).color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, x2, y, z).color(red, green, blue, alpha).endVertex();
    }


    private GuiRenderTypes(String name, Runnable setupState, Runnable clearState) {
        super(name, setupState, clearState);
    }
}