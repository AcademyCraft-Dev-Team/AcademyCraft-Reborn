package org.academy.api.client.gui.environment

import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.resources.Identifier
import org.academy.api.client.gui.texture.TextureSource
import org.academy.api.client.gui.unit.Density
import java.io.InputStream
import java.nio.file.Path

interface UiEnvironment {
    val guiScaledWidth: Int

    val guiScaledHeight: Int

    val guiScale: Float

    val density: Density get() = Density(guiScale)

    val physicalWidth: Int

    val physicalHeight: Int

    val gameDirectory: Path

    fun runOnMainThread(task: Runnable)

    fun frameDeltaTicks(): Float

    fun openResource(namespace: String, path: String): InputStream?

    fun loadTexture(identifier: Identifier): GpuTextureView

    fun createDynamicTextureSource(identifier: Identifier, bytes: ByteArray): TextureSource

    fun clipboard(): String = ""

    fun setClipboard(text: String) {
    }

    fun textInputFocusChanged(focused: Boolean) {
    }

    companion object {
        @Volatile
        private var delegate: UiEnvironment = MinecraftUiEnvironment()

        @JvmStatic
        fun get(): UiEnvironment = delegate

        @JvmStatic
        fun set(environment: UiEnvironment) {
            delegate = environment
        }
    }
}
