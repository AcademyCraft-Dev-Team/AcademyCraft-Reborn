package org.academy.api.client.gui.glyph

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import org.academy.api.client.gui.glyph.allocator.Rect
import org.academy.api.client.gui.glyph.allocator.SkylineAllocator
import java.nio.ByteBuffer

/** 一块图集页：GPU 纹理 + Skyline 分配器。 */
class AtlasPage(
    val size: Int,
    val format: GpuFormat,
    label: String
) {
    val texture: GpuTexture = RenderSystem.getDevice().createTexture(
        label,
        GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_COPY_SRC or GpuTexture.USAGE_TEXTURE_BINDING,
        format,
        size, size, 1, 1
    )

    val textureView: GpuTextureView = RenderSystem.getDevice().createTextureView(texture)

    private val allocator = SkylineAllocator(size, size)

    constructor(size: Int, label: String) : this(size, GpuFormat.RGBA8_UNORM, label)

    fun reserve(width: Int, height: Int): Rect? = allocator.allocate(width, height)

    fun upload(rect: Rect, buffer: ByteBuffer) {
        RenderSystem.assertOnRenderThread()
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.writeToTexture(texture, buffer, 0, 0, rect.x, rect.y, rect.width, rect.height)
    }

    fun close() {
        texture.close()
    }
}
