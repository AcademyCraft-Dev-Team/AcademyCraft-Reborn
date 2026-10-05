package org.academy.api.client.render.post;

import com.mojang.renderpearl.api.textures.GpuTextureView;

public final class BlurEffect {
    private BlurEffect() {
    }

    public static void apply(
            int width, int height,
            GpuTextureView sampler,
            GpuTextureView output,
            GpuTextureView depth,
            float radius
    ) {
        BackdropBlur.applyGaussian(sampler, output, depth, width, height, radius);
    }
}
