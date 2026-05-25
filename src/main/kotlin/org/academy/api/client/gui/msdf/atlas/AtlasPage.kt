package org.academy.api.client.gui.msdf.atlas

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import org.academy.api.client.gui.msdf.atlas.allocator.Rect
import org.academy.api.client.gui.msdf.atlas.allocator.SkylineAllocator
import java.lang.AutoCloseable
import java.util.*

class AtlasPage(val size: Int, label: String) : AutoCloseable {
    val texture: GpuTexture = RenderSystem.getDevice().createTexture(
        label,
        GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_COPY_SRC or GpuTexture.USAGE_TEXTURE_BINDING,
        TextureFormat.RGBA8,
        size, size, 1, 1
    )
    val textureView: GpuTextureView = RenderSystem.getDevice().createTextureView(texture)
    private val allocator: SkylineAllocator = SkylineAllocator(size, size)

    init {
        NativeImage(size, size, true).use { clearImage ->
            clearImage.fillRect(0, 0, size, size, 0x00000000)
            upload(Rect(0, 0, size, size), clearImage)
        }
    }

    fun reserve(width: Int, height: Int): Optional<Rect> {
        return allocator.allocate(width, height)
    }

    fun upload(rect: Rect, image: NativeImage) {
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.writeToTexture(texture, image, 0, 0, rect.x, rect.y, rect.width, rect.height, 0, 0)
    }

    override fun close() {
        texture.close()
    }
}