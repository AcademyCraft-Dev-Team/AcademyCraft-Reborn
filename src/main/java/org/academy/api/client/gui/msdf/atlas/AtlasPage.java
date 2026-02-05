package org.academy.api.client.gui.msdf.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import org.academy.api.client.gui.msdf.atlas.allocator.Rect;
import org.academy.api.client.gui.msdf.atlas.allocator.SkylineAllocator;

import java.util.Optional;

public class AtlasPage implements AutoCloseable {
    private final GpuTexture texture;
    private final GpuTextureView textureView;
    private final SkylineAllocator allocator;
    private final int size;

    public AtlasPage(int size, String label) {
        this.size = size;
        texture = RenderSystem.getDevice().createTexture(
                label,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8,
                size, size, 1, 1
        );
        textureView = RenderSystem.getDevice().createTextureView(texture);
        allocator = new SkylineAllocator(size, size);

        try (var clearImage = new NativeImage(size, size, true)) {
            clearImage.fillRect(0, 0, size, size, 0x00000000);
            upload(new Rect(0, 0, size, size), clearImage);
        }
    }

    public Optional<Rect> reserve(int width, int height) {
        return allocator.allocate(width, height);
    }

    public void upload(Rect rect, NativeImage image) {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToTexture(texture, image, 0, 0, rect.x(), rect.y(), rect.width(), rect.height(), 0, 0);
    }

    public GpuTexture getTexture() {
        return texture;
    }

    public GpuTextureView getTextureView() {
        return textureView;
    }

    public int getSize() {
        return size;
    }

    @Override
    public void close() {
        texture.close();
    }
}