package org.academy.api.client.render.vfxgraph.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jspecify.annotations.Nullable;

/**
 * 场景深度拷贝（M21 soft particles）：把当前深度附件拷到可采样的离屏纹理，
 * 供 billboard 片元着色器做 soft particles（近表面软化），避免采样渲染目标附件的反馈风险。
 * 大小/格式变化时自动重建；格式跟随源深度（D32_FLOAT / D24_UNORM_S8_UINT 等）。
 */
public final class SceneDepth {
    private GpuTexture texture;
    private GpuTextureView view;
    private int width;
    private int height;
    private GpuFormat format;

    /** 把深度附件拷贝到 scratch（大小/格式变化时重建）。必须在 render pass 外调用。 */
    public void copyFrom(GpuTextureView depth) {
        var source = depth.texture();
        int w = source.getWidth(0);
        int h = source.getHeight(0);
        if (texture == null || view == null || w != width || h != height || format != source.getFormat()) {
            release();
            width = w;
            height = h;
            format = source.getFormat();
            var device = RenderSystem.getDevice();
            texture = device.createTexture(
                    () -> "VfxGraph SceneDepth",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING,
                    format, width, height, 1, 1);
            view = device.createTextureView(texture);
        }
        RenderSystem.getDevice().createCommandEncoder()
                .copyTextureToTexture(source, texture, 0, 0, 0, 0, 0, width, height);
    }

    @Nullable
    public GpuTextureView view() {
        return view;
    }

    public void close() {
        release();
    }

    private void release() {
        if (view != null) {
            view.close();
        }
        if (texture != null) {
            texture.close();
        }
        texture = null;
        view = null;
    }
}
