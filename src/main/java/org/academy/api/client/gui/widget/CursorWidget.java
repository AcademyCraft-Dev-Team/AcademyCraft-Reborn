package org.academy.api.client.gui.widget;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.DynamicUniformStorage;
import org.academy.api.client.Render;
import org.academy.api.client.gui.command.PosTexRectDrawCommand;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.gui.framework.WidgetRenderContext;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;

public class CursorWidget extends AbstractWidget {
    public float radius = 0.25f;
    public float softness = 0.75f;

    public CursorWidget(float size) {
        super(0, 0, size, size);
    }

    @Override
    public void render(WidgetRenderContext context, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        var renderX = mouseX - getWidth() / 2f;
        var renderY = mouseY - getHeight() / 2f;

        context.pose().pushPose();
        context.pose().translate(renderX, renderY, getZ());

        submitGlowCommand(context, new Vector4f(0.0f, 0.0f, 0.0f, 0.8f));
        context.pose().translate(0, 0, 0.1f);
        submitGlowCommand(context, new Vector4f(1.0f, 1.0f, 1.0f, 0.8f));

        context.pose().popPose();
    }

    private void submitGlowCommand(WidgetRenderContext context, Vector4f color) {
        var sdfData = new SDFData(color, radius, softness);
        var glowCommand = new PosTexRectDrawCommand(
                Render.RenderPipelines.SDF_CIRCLE_GLOW,
                getWidth(),
                getHeight(),
                0,
                0,
                1,
                1
        ) {
            @Override
            public Map<String, GpuTextureView> getSamplers() {
                return Collections.emptyMap();
            }

            @Override
            public Map<String, GpuBufferSlice> getUniforms() {
                var uboStorage = context.getDynamicUbo(SDFData.class, SDFData.UBO_SIZE);
                var uboSlice = uboStorage.writeUniform(sdfData);
                return Map.of("GlowUniforms", uboSlice);
            }
        };
        context.submit(glowCommand);
    }

    public static class SDFData implements DynamicUniformStorage.DynamicUniform {
        public static final int UBO_SIZE = new Std140SizeCalculator().putVec4().putFloat().putFloat().get();
        private Vector4f color;
        private float radius;
        private float softness;

        public SDFData(Vector4f color, float radius, float softness) {
            this.color = color;
            this.radius = radius;
            this.softness = softness;
        }

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putVec4(color)
                    .putFloat(radius)
                    .putFloat(softness);
        }

        public Vector4f getColor() {
            return color;
        }

        public void setColor(Vector4f color) {
            this.color = color;
        }

        public float getRadius() {
            return radius;
        }

        public void setRadius(float radius) {
            this.radius = radius;
        }

        public float getSoftness() {
            return softness;
        }

        public void setSoftness(float softness) {
            this.softness = softness;
        }
    }
}