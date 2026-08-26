package org.academy.api.client.gui.environment

import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import java.io.InputStream
import java.nio.file.Path

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

    override fun clipboard(): String = mc.keyboardHandler.clipboard
    override fun setClipboard(text: String) {
        mc.keyboardHandler.clipboard = text
    }

    override fun textInputFocusChanged(focused: Boolean) {
        mc.textInputManager().onTextInputFocusChange(focused)
    }

    override fun layoutDir(): Path = gameDirectory.resolve("academy").resolve("ui")
}
