package org.academy.api.client.gui.texture

import com.mojang.renderpearl.api.textures.GpuSampler
import com.mojang.renderpearl.api.textures.GpuTextureView
import net.minecraft.resources.Identifier
import org.academy.AcademyCraft
import org.academy.api.client.gui.environment.UiEnvironment

class IdentifierTextureSource(
    val identifier: Identifier,
    private val environment: UiEnvironment = UiEnvironment.get()
) : TextureSource {
    private var cachedView: GpuTextureView? = null

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
