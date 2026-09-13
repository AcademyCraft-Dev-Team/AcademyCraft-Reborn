package org.academy.desktop.platform

import com.mojang.blaze3d.opengl.GlBackend
import com.mojang.blaze3d.platform.DisplayData
import com.mojang.blaze3d.platform.MonitorManager
import com.mojang.blaze3d.platform.Window
import com.mojang.blaze3d.shaders.GpuDebugOptions
import com.mojang.blaze3d.systems.GpuDevice
import com.mojang.blaze3d.systems.GpuSurface
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.systems.SurfaceException
import net.minecraft.util.Util
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.text.font.MsdfFontService
import org.academy.api.client.render.Render
import org.lwjgl.glfw.GLFW
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

object DesktopApplication {
    private val LOGGER: Logger = LoggerFactory.getLogger(DesktopApplication::class.java)

    private const val NANOS_PER_TICK = 50_000_000L

    fun run(app: EditorApp, environment: DesktopEnvironment) {
        UiEnvironment.set(environment)

        RenderSystem.initRenderThread()
        Util.setTimeSource(RenderSystem.initBackendSystem())

        ClasspathShaderSource.sourceDir = environment.workingDir.resolve("src").resolve("main").resolve("resources")

        GLFW.glfwDefaultWindowHints()
        GLFW.glfwWindowHint(131088, GLFW.GLFW_TRUE)

        val host = DesktopUiHost(app, environment)
        val window = Window(
            host,
            DisplayData(
                environment.physicalWidth,
                environment.physicalHeight,
                OptionalInt.empty(),
                OptionalInt.empty(),
                false
            ),
            null,
            false,
            app.title,
            MonitorManager(),
            GlBackend()
        )

        val device = window.backend().createDevice(
            window.handle(),
            ClasspathShaderSource::read,
            GpuDebugOptions(0, false, false, false)
        ) { }
        RenderSystem.initRenderer(device)
        val surface = device.createSurface(window.handle())

        host.bind(window)

        configureSurface(surface, window)
        precompilePipelines(device)
        Render.Buffers.init()
        initFonts()

        GLFW.glfwShowWindow(window.handle())

        var lastNanos = Util.getNanos()
        var lastTitle: String? = null
        while (!window.shouldClose() && !app.quitRequested()) {
            val now = Util.getNanos()
            val partialTick = ((now - lastNanos).toFloat() / NANOS_PER_TICK).coerceIn(0f, 1f)
            lastNanos = now

            if (app.title != lastTitle) {
                lastTitle = app.title
                window.setTitle(app.title)
            }

            RenderSystem.pollEvents()

            if (host.surfaceNeedsReconfigure) {
                host.surfaceNeedsReconfigure = false
                try {
                    configureSurface(surface, window)
                } catch (e: SurfaceException) {
                    LOGGER.warn("Couldn't reconfigure surface", e)
                }
            }

            if (!window.isMinimized) {
                try {
                    surface.acquireNextTexture()
                } catch (e: SurfaceException) {
                    LOGGER.warn("Couldn't acquire surface texture", e)
                }
            }

            host.frame(partialTick)

            if (surface.isAcquired) {
                val color = host.target?.getColorTextureView()
                if (color != null) {
                    surface.blitFromTexture(device.createCommandEncoder(), color)
                }
            }
            device.createCommandEncoder().submit()
            if (surface.isAcquired) {
                surface.present()
            }
        }

        host.close()
        app.onDispose()
        try {
            RenderSystem.shutdownRenderer()
        } catch (e: Exception) {
            LOGGER.warn("Failed to shut down renderer cleanly", e)
        }
        window.close()
    }

    private fun configureSurface(surface: GpuSurface, window: Window) {
        val presentMode = GpuSurface.PresentMode.getSupportedVsyncMode(surface.supportedPresentModes(), true)
        surface.configure(GpuSurface.Configuration(window.width, window.height, presentMode))
    }

    private fun precompilePipelines(device: GpuDevice) {
        val needed = listOf(
            Render.RenderPipelines.POS_COLOR,
            Render.RenderPipelines.IMAGE,
            Render.RenderPipelines.IMAGE_PREMULTIPLIED_ALPHA,
            Render.RenderPipelines.IMAGE_CIRCLE,
            Render.RenderPipelines.IMAGE_MONOCHROME,
            Render.RenderPipelines.MSDF_TEXT,
            Render.RenderPipelines.SDF_SHARP_MARGIN,
            Render.RenderPipelines.SKILL_PROGRESS,
            Render.RenderPipelines.IMGUI,
        )
        for (pipeline in needed) {
            try {
                device.precompilePipeline(pipeline, ClasspathShaderSource::read)
            } catch (e: Exception) {
                LOGGER.warn("Failed to precompile pipeline {}", pipeline.location, e)
            }
        }
    }

    private fun initFonts() {
        try {
            MsdfFontService.loadFont(MsdfFontService.DEFAULT_FONT_ID)
            val cjkFont = MsdfFontService.DEFAULT_FONT_ID.withPath("fonts/wqy-microhei-modified.ttf")
            MsdfFontService.loadFont(cjkFont)
            MsdfFontService.setFontSearchOrder(listOf(MsdfFontService.DEFAULT_FONT_ID, cjkFont))
        } catch (e: Exception) {
            LOGGER.warn("Failed to initialize fonts", e)
        }
    }
}
