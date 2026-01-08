package org.academy.api.client.hud.ability;

import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.academy.api.client.Render;
import org.academy.api.client.Resource;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.render.UiContext;
import org.academy.api.client.gui.widget.FrameLayoutWidget;
import org.academy.api.client.gui.widget.ImageWidget;
import org.academy.api.client.gui.widget.WidgetContainer;
import org.academy.api.client.gui.widget.WidgetContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AbilityInfoHUD {
    @Nullable
    private static AbilityInfoHUD INSTANCE;

    private final Context context = new Context();
    private final UiContext uiContext = new UiContext();

    private AbilityInfoHUD() {
    }

    public static AbilityInfoHUD getInstance() {
        if (INSTANCE == null) throw new IllegalStateException("AbilityInfoHUD has not been initialized.");
        return INSTANCE;
    }

    public static void initMain() {
        INSTANCE = new AbilityInfoHUD();
    }

    public void perform(double mouseX, double mouseY, float deltaPartialTick) {
        uiContext.perform(context.get(), mouseX, mouseY, deltaPartialTick);
    }

    public void render(
            int width, int height,
            GpuTextureView color,
            GpuTextureView depth,
            AtomicBoolean drew
    ) {
        if (!AbilitySystemClient.isActiveHUD()) return;

        var desc = new RenderTargetDescriptor(
                width, height,
                true, 0
        );
        var terminalTarget = Render.Buffers.getResourcePool().acquire(desc);

        try {
            uiContext.upload(terminalTarget, false);

            var hudView = terminalTarget.getColorTextureView();
            if (hudView == null) return;

            var commandEncoder = RenderSystem.getDevice().createCommandEncoder();

            var dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                    new Matrix4f(), new Vector4f(1),
                    new Vector3f(), new Matrix4f()
            );

            var projection = Render.Buffers.getInstance().getProjectionUB(new Matrix4f()).slice();
            try (var renderPass = commandEncoder.createRenderPass(
                    () -> "Blit Pass to " + color + depth,
                    color, OptionalInt.empty(), depth, OptionalDouble.empty()
            )) {
                renderPass.setPipeline(Render.RenderPipelines.IMAGE_STENCIL_PREMULTIPLIED_ALPHA);
                renderPass.bindTexture(
                        "Sampler0",
                        hudView,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                );
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("Projection", projection);
                renderPass.setUniform("DynamicTransforms", dynamicTransforms);

                renderPass.setVertexBuffer(0, Render.Buffers.getInstance().getFSQuadColorVBNDC());
                var sequentialBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                renderPass.setIndexBuffer(sequentialBuffer.getBuffer(6), sequentialBuffer.type());
                renderPass.drawIndexed(0, 0, 6, 1);
            }
            drew.set(true);
        } finally {
            Render.Buffers.getResourcePool().release(desc, terminalTarget);
        }
    }

    private static class Context implements WidgetContext {
        private final FrameLayoutWidget root = createRoot();

        @Override
        public WidgetContainer get() {
            return root;
        }

        private FrameLayoutWidget createRoot() {
            var root = new FrameLayoutWidget();
            {
                var icon = new ImageWidget(Resource.Textures.LOGO_TECH);
                icon.setLayoutParams(
                        new FrameLayoutWidget.LayoutParams()
                                .size(128, 64)
                                .gravity(Gravity.START)
                );
                root.addChild("icon", icon);
            }
            return root;
        }
    }
}