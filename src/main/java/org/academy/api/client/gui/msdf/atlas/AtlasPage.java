package org.academy.api.client.gui.msdf.atlas;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.academy.api.client.gui.msdf.atlas.allocator.Rect;
import org.academy.api.client.gui.msdf.atlas.allocator.SkylineAllocator;

import java.nio.ByteBuffer;
import java.util.Optional;

public class AtlasPage {
    public final int size;
    public final GpuTexture texture;
    public final GpuTextureView textureView;
    private final SkylineAllocator allocator;

    public AtlasPage(int size, String label) {
        this.size = size;
        texture = RenderSystem.getDevice().createTexture(
                label,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING,
                GpuFormat.RGBA8_UNORM,
                size, size, 1, 1
        );
        textureView = RenderSystem.getDevice().createTextureView(texture);
        allocator = new SkylineAllocator(size, size);
    }

    public Optional<Rect> reserve(int width, int height) {
        return allocator.allocate(width, height);
    }

    public void upload(Rect rect, ByteBuffer buffer) {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToTexture(
                texture, buffer,
                0, 0, rect.x(), rect.y(), rect.width(), rect.height()
        );
    }

    public void close() {
        texture.close();
    }
}
