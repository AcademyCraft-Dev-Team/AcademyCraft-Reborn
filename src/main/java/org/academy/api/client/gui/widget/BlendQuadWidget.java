package org.academy.api.client.gui.widget;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniformStorage;
import org.academy.api.client.Render;
import org.academy.api.client.Resource;
import org.academy.api.client.gui.command.ImageDrawCommand;
import org.academy.api.client.gui.command.PosTexRectDrawCommand;
import org.academy.api.client.gui.render.RenderContext;
import org.academy.api.client.render.UniformPayload;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.List;

public class BlendQuadWidget extends AbstractWidget {
    public float marginTop = 4f;
    public float marginBottom = 4f;
    public float marginLeft = 4f;
    public float marginRight = 4f;
    public boolean drawLine = true;

    public float red;
    public float green;
    public float blue;

    @Override
    public void render(RenderContext context) {
        if (!isVisible()) return;

        var lp = getLayoutParams();
        var paddedWidth = getWidth() - lp.paddingLeft - lp.paddingRight;
        var paddedHeight = getHeight() - lp.paddingTop - lp.paddingBottom;

        // paddedHeight == 0 是预期行为喵
        if (paddedWidth <= 0 || paddedHeight < 0) return;

        var finalAlpha = getAlpha() * context.getAccumulatedAlpha();

        context.pose().pushPose();
        context.pose().translate(lp.paddingLeft, lp.paddingTop, 0);
        context.drawOrder().push();
        {
            // 极小值也需要渲染喵
            if (finalAlpha != 0) {
                var sdfCommand = new PosTexRectDrawCommand(
                        Render.RenderPipelines.SDF_SHARP_MARGIN,
                        paddedWidth,
                        paddedHeight,
                        0.0f,
                        0.0f,
                        1.0f,
                        1.0f,
                        List.of(),
                        List.of(new UniformPayload<>(
                                        "SdfUniforms",
                                SDFData.class, new SDFData(
                                                new Vector2f(paddedWidth, paddedHeight),
                                                new Vector4f(
                                                        marginLeft, marginTop, marginRight, marginBottom
                                                ),
                                                new Vector4f(red, green, blue, finalAlpha)
                                        ),
                                SDFData.UBO_SIZE
                                )
                        )
                );
                context.submit(sdfCommand);
            }

            if (drawLine) {
                context.drawOrder().advance();
                renderLines(context, context.getAccumulatedAlpha(), paddedWidth, paddedHeight);
            }
        }
        context.drawOrder().pop();
        context.pose().popPose();
    }

    private void renderLines(RenderContext context, float finalAlpha, float paddedWidth, float paddedHeight) {
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var textureManager = Minecraft.getInstance().getTextureManager();
        var lineTextureView = textureManager.getTexture(Resource.Textures.ELEMENT_LINE).getTextureView();
        var lineH = 4.0f;
        {
            var topLineCommand = new ImageDrawCommand(lineTextureView, sampler, paddedWidth, lineH, 0, 0, 1, 1, 1.0f, 1.0f, 1.0f, finalAlpha);
            context.submit(topLineCommand);
        }
        {
            context.pose().pushPose();
            context.pose().translate(0, Math.max(paddedHeight - lineH, 0), 0);
            var bottomLineCommand = new ImageDrawCommand(lineTextureView, sampler, paddedWidth, lineH, 0, 0, 1, 1, 1.0f, 1.0f, 1.0f, finalAlpha);
            context.submit(bottomLineCommand);
            context.pose().popPose();
        }
    }

    public record SDFData(Vector2f size, Vector4f margins,
                          Vector4f fillColor) implements DynamicUniformStorage.DynamicUniform {
        public static final int UBO_SIZE = new Std140SizeCalculator().putVec2().putVec4().putVec4().get();

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer).putVec2(size).putVec4(margins).putVec4(fillColor);
        }
    }
}