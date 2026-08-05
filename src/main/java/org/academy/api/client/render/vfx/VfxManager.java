package org.academy.api.client.render.vfx;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import org.academy.api.client.render.post.PostEffect;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

public final class VfxManager {
    public static final VfxManager INSTANCE = new VfxManager();

    private final Set<Vfx> activeEffects = new LinkedHashSet<>();
    private final VfxFrameData frameData = new VfxFrameData();
    private final MainRenderContext renderContext = new MainRenderContext();
    private boolean initialized;

    private VfxManager() {
    }

    public void init() {
        if (initialized) return;
        var device = RenderSystem.getDevice();
        VfxRegistry.forEachRenderer(renderer -> renderer.init(device));
        initialized = true;
    }

    public void close() {
        if (!initialized) return;
        VfxRegistry.forEachRenderer(VfxRenderer::close);
        activeEffects.clear();
        frameData.clear();
        initialized = false;
    }

    public void spawn(Vfx effect) {
        activeEffects.add(effect);
    }

    public void clearEffects() {
        activeEffects.clear();
    }

    public void sampleFrame(VfxFrameContext ctx) {
        frameData.clear();
        renderContext.update(ctx);
        var iterator = activeEffects.iterator();
        while (iterator.hasNext()) {
            var effect = iterator.next();
            effect.update(ctx.dt(), ctx);
            if (!effect.isAlive()) {
                iterator.remove();
                continue;
            }
            effect.sample(ctx, frameData);
        }
    }

    public void renderFrame() {
        if (!initialized) return;
        if (frameData.isEmpty()) return;
        VfxRegistry.renderPhase(VfxPhase.WORLD_TRANSLUCENT, frameData, renderContext);
        VfxRegistry.renderPhase(VfxPhase.WORLD_ALWAYS_ON_TOP, frameData, renderContext);
        VfxRegistry.renderPhase(VfxPhase.SCREEN_SPACE_POST, frameData, renderContext);
        if (!hasPhaseData(VfxPhase.WORLD_GLOW)) {
            frameData.clear();
        }
    }

    public boolean hasGlowData() {
        return hasPhaseData(VfxPhase.WORLD_GLOW);
    }

    public void renderGlowFrame(GpuTextureView color, @Nullable GpuTextureView depth) {
        if (!initialized) return;
        renderContext.setBloomInput(color, depth);
        try {
            VfxRegistry.renderPhase(VfxPhase.WORLD_GLOW, frameData, renderContext);
        } finally {
            renderContext.setBloomInput(null, null);
            frameData.clear();
        }
    }

    private boolean hasPhaseData(VfxPhase phase) {
        for (var registration : VfxRegistry.entries()) {
            if (registration.phase() != phase) continue;
            var bucket = frameData.get(registration.dataType());
            if (bucket != null && !bucket.isEmpty()) return true;
        }
        return false;
    }

    private static final class MainRenderContext implements VfxRenderContext {
        private final Vector3f cameraPos = new Vector3f();
        private final Quaternionf cameraOrientation = new Quaternionf();
        private @Nullable GpuTextureView bloomInputColor;
        private @Nullable GpuTextureView bloomInputDepth;
        private float gameTime;

        void update(VfxFrameContext frame) {
            gameTime = frame.gameTime();
            cameraPos.set(frame.camera().pos());
            cameraOrientation.set(frame.camera().orientation());
        }

        void setBloomInput(@Nullable GpuTextureView color, @Nullable GpuTextureView depth) {
            bloomInputColor = color;
            bloomInputDepth = depth;
        }

        @Override
        public GpuDevice device() {
            return RenderSystem.getDevice();
        }

        @Override
        public float gameTime() {
            return gameTime;
        }

        @Override
        public Vector3f cameraPos() {
            return cameraPos;
        }

        @Override
        public Quaternionf cameraOrientation() {
            return cameraOrientation;
        }

        @Override
        public @Nullable GpuTextureView mainColor() {
            return Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView();
        }

        @Override
        public @Nullable GpuTextureView mainDepth() {
            return Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView();
        }

        @Override
        public @Nullable GpuTextureView bloomInputColor() {
            return bloomInputColor;
        }

        @Override
        public @Nullable GpuTextureView bloomInputDepth() {
            return bloomInputDepth;
        }

        @Override
        public @Nullable GpuTextureView sceneColor() {
            return PostEffect.MAIN_SCENE.getColorTextureView();
        }
    }
}
