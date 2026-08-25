package org.academy.api.client.render.post;

import com.mojang.blaze3d.textures.GpuTextureView;

public final class BlurEffect {
    private BlurEffect() {
    }

    /**
     * 应用高斯模糊喵 (复用 [BackdropBlur] 的唯一模糊引擎)
     *
     * @param width   采样宽度喵
     * @param height  采样高度喵
     * @param sampler 采样目标喵
     * @param output  输出目标喵
     * @param depth   模板喵
     * @param radius  模糊半径喵
     */
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
