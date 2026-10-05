package org.academy.api.client.render.vfxgraph.render;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.jspecify.annotations.Nullable;

public final class VfxGlowHooks {
    public interface GlowLinePass {
        boolean hasWork();

        void render(GpuTextureView color, @Nullable GpuTextureView depth);
    }

    private static @Nullable GlowLinePass glowLines;

    private VfxGlowHooks() {
    }

    public static void registerGlowLines(@Nullable GlowLinePass pass) {
        glowLines = pass;
    }

    public static boolean hasGlowLines() {
        return glowLines != null && glowLines.hasWork();
    }

    public static void renderGlowLines(GpuTextureView color, @Nullable GpuTextureView depth) {
        if (glowLines != null) glowLines.render(color, depth);
    }
}