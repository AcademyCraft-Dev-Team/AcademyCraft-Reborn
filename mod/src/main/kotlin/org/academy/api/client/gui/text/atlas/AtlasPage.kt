package org.academy.api.client.gui.text.atlas

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.textures.GpuTexture
import com.mojang.renderpearl.api.textures.GpuTextureView
import java.nio.ByteBuffer

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

    fun reserve(width: Int, height: Int): AtlasRect? = allocator.allocate(width, height)

    fun upload(rect: AtlasRect, buffer: ByteBuffer) {
        RenderSystem.assertOnRenderThread()
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.writeToTexture(texture, buffer, 0, 0, rect.x, rect.y, rect.width, rect.height)
    }

    fun close() {
        texture.close()
    }
}
