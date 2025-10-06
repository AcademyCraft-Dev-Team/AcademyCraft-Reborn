package org.academy.api.client.gui.widget;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniformStorage;
import org.academy.api.client.Render;
import org.academy.api.client.Resource;
import org.academy.api.client.gui.command.ImageDrawCommand;
import org.academy.api.client.gui.command.PosTexRectDrawCommand;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.gui.framework.WidgetRenderContext;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;

public class BlendQuadWidget extends AbstractWidget {
    public float marginTop = 4f;
    public float marginBottom = 4f;
    public float marginLeft = 4f;
    public float marginRight = 4f;
    public boolean drawLine = true;

    public float red;
    public float green;
    public float blue;

    public BlendQuadWidget(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    @Override
    public void render(WidgetRenderContext context, double mouseX, double mouseY, float partialTick) {
        if (!isVisible())
            return;

        var finalAlpha = getAlpha() * context.getAccumulatedAlpha();

        context.pose().pushPose();
        context.pose().translate(getX(), getY(), getZ());
        {
            var sdfCommand = new PosTexRectDrawCommand(
                    Render.RenderPipelines.SDF_SHARP_MARGIN,
                    getWidth(),
                    getHeight(),
                    0.0f,
                    0.0f,
                    1.0f,
                    1.0f
            ) {
                @Override
                public Map<String, GpuTextureView> getSamplers() {
                    return Collections.emptyMap();
                }

                @Override
                public Map<String, GpuBufferSlice> getUniforms() {
                    var uboStorage = context.getDynamicUbo(SDFData.class, SDFData.UBO_SIZE);
                    var size = new Vector2f(getWidth(), getHeight());
                    var margins = new Vector4f(marginLeft, marginTop, marginRight, marginBottom);
                    var fillColor = new Vector4f(red, green, blue, finalAlpha);
                    var sdfData = new SDFData(size, margins, fillColor);
                    var uboSlice = uboStorage.writeUniform(sdfData);
                    return Map.of("SdfUniforms", uboSlice);
                }
            };
            context.submit(sdfCommand);

            if (this.drawLine) {
                this.renderLines(context, context.getAccumulatedAlpha());
            }
        }
        context.pose().popPose();
    }

    private void renderLines(WidgetRenderContext context, float finalAlpha) {
        var textureManager = Minecraft.getInstance().getTextureManager();
        var lineTexture = textureManager.getTexture(Resource.Textures.ELEMENT_LINE_TEXTURE).getTextureView();
        var lineW = getWidth() - 2.0f;
        var lineH = 4.0f;

        {
            context.pose().pushPose();
            context.pose().translate(1.0f, 0.0f, 0.1f);
            var topLineCommand = new ImageDrawCommand(lineTexture, lineW, lineH, 0, 0, 1, 1, 1.0f, 1.0f, 1.0f, finalAlpha);
            context.submit(topLineCommand);
            context.pose().popPose();
        }
        {
            context.pose().pushPose();
            var bottomLineY = getHeight() - marginTop / 2.0f;
            context.pose().translate(1.0f, bottomLineY, 0.1f);
            var bottomLineCommand = new ImageDrawCommand(lineTexture, lineW, lineH, 0, 0, 1, 1, 1.0f, 1.0f, 1.0f, finalAlpha);
            context.submit(bottomLineCommand);
            context.pose().popPose();
        }
    }

    public record SDFData(Vector2f size, Vector4f margins, Vector4f fillColor) implements DynamicUniformStorage.DynamicUniform {
        public static final int UBO_SIZE = new Std140SizeCalculator().putVec2().putVec4().putVec4().get();

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer).putVec2(size).putVec4(margins).putVec4(fillColor);
        }
    }
}