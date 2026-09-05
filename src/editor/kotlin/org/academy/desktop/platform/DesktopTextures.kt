package org.academy.desktop.platform

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.resources.Identifier
import org.lwjgl.system.MemoryUtil
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

object DesktopTextures {
    private val textures = ConcurrentHashMap<Identifier, GpuTexture>()

    private val dynamicTextures = ConcurrentHashMap<Identifier, GpuTexture>()

    private val missing: GpuTextureView by lazy {
        RenderSystem.getDevice().createTextureView(createSolid(1, 1, 0xFF000000.toInt()))
    }

    fun register(identifier: Identifier, bytes: ByteArray) {
        val texture = NativeImage.read(ByteArrayInputStream(bytes)).use { upload(it) }
        dynamicTextures[identifier]?.close()
        dynamicTextures[identifier] = texture
    }

    fun load(identifier: Identifier, input: InputStream?): GpuTextureView {
        dynamicTextures[identifier]?.let { return RenderSystem.getDevice().createTextureView(it) }
        if (input == null) return missing
        val texture = textures.computeIfAbsent(identifier) {
            try {
                input.use { stream -> NativeImage.read(stream) }.let { image ->
                    val uploaded = upload(image)
                    image.close()
                    uploaded
                }
            } catch (_: Exception) {
                createSolid(1, 1, 0xFFFF00FF.toInt())
            }
        }
        return RenderSystem.getDevice().createTextureView(texture)
    }

    private fun upload(image: NativeImage): GpuTexture {
        val device = RenderSystem.getDevice()
        val width = image.width
        val height = image.height
        val texture = device.createTexture(
            "ac_tex",
            GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_COPY_SRC or GpuTexture.USAGE_TEXTURE_BINDING,
            GpuFormat.RGBA8_UNORM,
            width, height, 1, 1
        )
        val bytes = MemoryUtil.memAlloc(width * height * 4)
        for (pixel in image.pixelsABGR) {
            bytes.put((pixel and 0xFF).toByte())
            bytes.put(((pixel shr 8) and 0xFF).toByte())
            bytes.put(((pixel shr 16) and 0xFF).toByte())
            bytes.put(((pixel shr 24) and 0xFF).toByte())
        }
        bytes.flip()
        val encoder = device.createCommandEncoder()
        encoder.writeToTexture(texture, bytes, 0, 0, 0, 0, width, height)
        MemoryUtil.memFree(bytes)
        return texture
    }

    private fun createSolid(width: Int, height: Int, argb: Int): GpuTexture {
        val device = RenderSystem.getDevice()
        val texture = device.createTexture(
            "ac_missing",
            GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_COPY_SRC or GpuTexture.USAGE_TEXTURE_BINDING,
            GpuFormat.RGBA8_UNORM,
            width, height, 1, 1
        )
        val bytes = MemoryUtil.memAlloc(width * height * 4)
        repeat(width * height) {
            bytes.put(((argb shr 16) and 0xFF).toByte())
            bytes.put(((argb shr 8) and 0xFF).toByte())
            bytes.put((argb and 0xFF).toByte())
            bytes.put(((argb shr 24) and 0xFF).toByte())
        }
        bytes.flip()
        val encoder = device.createCommandEncoder()
        encoder.writeToTexture(texture, bytes, 0, 0, 0, 0, width, height)
        MemoryUtil.memFree(bytes)
        return texture
    }

    fun close() {
        textures.values.forEach { it.close() }
        dynamicTextures.values.forEach { it.close() }
        textures.clear()
        dynamicTextures.clear()
    }
}
