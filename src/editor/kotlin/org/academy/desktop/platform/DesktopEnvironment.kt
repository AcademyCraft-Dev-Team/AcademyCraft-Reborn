package org.academy.desktop.platform

import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.resources.Identifier
import org.academy.AcademyCraft
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.texture.IdentifierTextureSource
import org.academy.api.client.gui.texture.TextureSource
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue

class DesktopEnvironment(
    val workingDir: Path,
    initialWidth: Int,
    initialHeight: Int,
    initialGuiScale: Float = 2f,
) : UiEnvironment {
    @Volatile
    override var physicalWidth: Int = initialWidth
        internal set

    @Volatile
    override var physicalHeight: Int = initialHeight
        internal set

    @Volatile
    override var guiScale: Float = initialGuiScale

    private val mainThreadTasks = ConcurrentLinkedQueue<Runnable>()

    override val guiScaledWidth: Int get() = (physicalWidth / guiScale).toInt()
    override val guiScaledHeight: Int get() = (physicalHeight / guiScale).toInt()
    override val gameDirectory: Path get() = workingDir

    override fun runOnMainThread(task: Runnable) {
        mainThreadTasks.add(task)
    }

    @Volatile
    var frameDeltaTicks: Float = 1f
        internal set

    override fun frameDeltaTicks(): Float = frameDeltaTicks

    var clipboardGetter: () -> String = { "" }
    var clipboardSetter: (String) -> Unit = {}

    @Volatile
    var imguiBackend: org.academy.internal.client.gui.imgui.ImGuiBackend? = null

    override fun clipboard(): String = clipboardGetter()
    override fun setClipboard(text: String) {
        clipboardSetter(text)
    }

    fun drainMainThreadTasks() {
        while (true) {
            val task = mainThreadTasks.poll() ?: return
            task.run()
        }
    }

    override fun openResource(namespace: String, path: String): InputStream? {
        val sourceFile = workingDir.resolve("src").resolve("main").resolve("resources")
            .resolve("assets").resolve(namespace).resolve(path)
        if (Files.isRegularFile(sourceFile)) return Files.newInputStream(sourceFile)
        return javaClass.getResourceAsStream("/assets/$namespace/$path")
    }

    override fun loadTexture(identifier: Identifier): GpuTextureView {
        val input = openResource(identifier.namespace, identifier.path)
        return DesktopTextures.load(identifier, input)
    }

    override fun createDynamicTextureSource(identifier: Identifier, bytes: ByteArray): TextureSource {
        DesktopTextures.register(identifier, bytes)
        return IdentifierTextureSource(identifier, this)
    }

    override fun layoutDir(): Path = workingDir.resolve("src").resolve("main").resolve("resources")
        .resolve("assets").resolve(AcademyCraft.MOD_ID).resolve("ui").resolve("layout")
}
