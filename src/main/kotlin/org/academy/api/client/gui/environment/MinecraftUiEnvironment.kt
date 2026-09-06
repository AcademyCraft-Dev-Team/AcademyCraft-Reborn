package org.academy.api.client.gui.environment

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.academy.api.client.gui.texture.IdentifierTextureSource
import org.academy.api.client.gui.texture.TextureSource
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Path
import java.util.function.Supplier

/**
 * Default [UiEnvironment] backing onto a running Minecraft client. Keeps the
 * in-game behavior of the UI framework identical to before the abstraction.
 */
class MinecraftUiEnvironment : UiEnvironment {
    private val mc get() = Minecraft.getInstance()

    override val guiScaledWidth: Int get() = mc.window.guiScaledWidth
    override val guiScaledHeight: Int get() = mc.window.guiScaledHeight
    override val guiScale: Float get() = mc.window.guiScale.toFloat()
    override val physicalWidth: Int get() = mc.window.width
    override val physicalHeight: Int get() = mc.window.height
    override val gameDirectory: Path get() = mc.gameDirectory.toPath()
    override fun runOnMainThread(task: Runnable) = mc.execute(task)
    override fun frameDeltaTicks(): Float = mc.deltaTracker.gameTimeDeltaTicks
    override fun openResource(namespace: String, path: String): InputStream? =
        mc.resourceManager.getResource(Identifier.fromNamespaceAndPath(namespace, path)).map { it.open() }.orElse(null)

    override fun loadTexture(identifier: Identifier): GpuTextureView =
        mc.textureManager.getTexture(identifier).getTextureView()

    override fun createDynamicTextureSource(identifier: Identifier, bytes: ByteArray): TextureSource {
        val image = NativeImage.read(ByteArrayInputStream(bytes))
        val texture = DynamicTexture(Supplier { "academy_${identifier.namespace}_${identifier.path}" }, image)
        mc.textureManager.register(identifier, texture)
        return IdentifierTextureSource(identifier, this)
    }

    override fun clipboard(): String = mc.keyboardHandler.clipboard
    override fun setClipboard(text: String) {
        mc.keyboardHandler.clipboard = text
    }

    override fun textInputFocusChanged(focused: Boolean) {
        mc.textInputManager().onTextInputFocusChange(focused)
    }

    override fun layoutDir(): Path = gameDirectory.resolve("academy").resolve("ui")
}
