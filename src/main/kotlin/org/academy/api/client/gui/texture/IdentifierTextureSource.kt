package org.academy.api.client.gui.texture

import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.resources.Identifier
import org.academy.AcademyCraft
import org.academy.api.client.gui.environment.UiEnvironment

/**
 * 以 [Identifier] 为来源的纹理源: 静态资源文件, 或 texture manager 中已注册的动态纹理 key.
 *
 * 懒解析并缓存 GPU view; 当缓存 view 被关闭 (如同 key 动态纹理被重新注册、资源重载) 时
 * 自动经 [UiEnvironment.loadTexture] 重解析 —— 天然解决"同 key 重注册后持旧 view"的问题.
 */
class IdentifierTextureSource(
    private val identifier: Identifier,
    private val environment: UiEnvironment = UiEnvironment.get()
) : TextureSource {
    private var cachedView: GpuTextureView? = null

    fun getIdentifier(): Identifier = identifier

    override fun getTextureView(): GpuTextureView? {
        val current = cachedView
        if (current != null && !current.isClosed) return current
        return try {
            environment.loadTexture(identifier).also { cachedView = it }
        } catch (e: Exception) {
            cachedView = null
            logger.error("Failed to resolve texture view for {}", identifier, e)
            null
        }
    }

    override fun getPreferredSampler(): GpuSampler? = null

    override fun toString(): String = "IdentifierTextureSource($identifier)"

    companion object {
        private val logger = AcademyCraft.getLogger()
    }
}

/** 静态图片 (资源文件) 源. */
fun fromFile(identifier: Identifier): TextureSource = IdentifierTextureSource(identifier)

/** 动态纹理 (texture manager 中已注册的 key) 源. */
fun fromKey(identifier: Identifier): TextureSource = IdentifierTextureSource(identifier)