package org.academy.desktop.platform

import com.mojang.blaze3d.pipeline.PipelineCache
import com.mojang.blaze3d.platform.DisplayData
import com.mojang.blaze3d.platform.MonitorManager
import com.mojang.blaze3d.platform.Window
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.device.GpuDebugOptions
import com.mojang.renderpearl.api.device.GpuDevice
import com.mojang.renderpearl.api.device.GpuSurface
import com.mojang.renderpearl.api.device.SurfaceException
import com.mojang.renderpearl.backend.opengl.GlBackend
import net.minecraft.util.Util
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.text.font.FontRepository
import org.academy.api.client.render.Render
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.function.BooleanSupplier

object DesktopApplication {
    private val LOGGER: Logger = LoggerFactory.getLogger(DesktopApplication::class.java)

    private const val NANOS_PER_TICK = 50_000_000L

    fun run(app: EditorApp, environment: DesktopEnvironment) {
        UiEnvironment.set(environment)

        RenderSystem.initRenderThread()
        Util.setTimeSource(RenderSystem.initBackendSystem())

        ClasspathShaderSource.sourceDir = modResources(environment.workingDir)

        val backend = GlBackend()
        try {
            backend.loadLibrary()
        } catch (e: Exception) {
            LOGGER.error("Failed to load OpenGL backend", e)
            return
        }
        val device = backend.createDevice(GpuDebugOptions(0, false, false, false))
        RenderSystem.initRenderer(device)
        RenderSystem.trackBackendLibraryForShutdown(backend)

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
            backend
        )

        val surface = device.createSurface(window.handle(), BooleanSupplier { window.isIconified })
        host.bind(window)

        configureSurface(surface, window)
        precompilePipelines(device)
        Render.Buffers.init()
        initFonts()

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

            host.pollEvents()

            if (host.surfaceNeedsReconfigure) {
                host.surfaceNeedsReconfigure = false
                try {
                    configureSurface(surface, window)
                } catch (e: SurfaceException) {
                    LOGGER.warn("Couldn't reconfigure surface", e)
                }
            }

            if (!window.isIconified) {
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
            surface.close()
        } catch (e: Exception) {
            LOGGER.warn("Failed to close surface", e)
        }
        try {
            RenderSystem.shutdownRenderer()
        } catch (e: Exception) {
            LOGGER.warn("Failed to shut down renderer cleanly", e)
        }
        window.close()
        RenderSystem.unloadTrackedBackendLibrary()
    }

    /**
     * 着色器热重载：换上一份全新的 [PipelineCache]（仍从 [ClasspathShaderSource] 读取），
     * 使下一次 `RenderSystem.getCompiledPipeline` 重新编译源 shader。
     */
    fun reloadShaderPipelines() {
        val device = RenderSystem.tryGetDevice() ?: return
        val cache = PipelineCache(device, ClasspathShaderSource)
        val old = RenderSystem.setCurrentPipelineCache(cache)
        old?.close()
        LOGGER.info("Shader pipeline cache reloaded")
    }

    private fun configureSurface(surface: GpuSurface, window: Window) {
        val presentMode = GpuSurface.PresentMode.getSupportedVsyncMode(surface.supportedPresentModes(), true)
        surface.configure(GpuSurface.Configuration(window.width, window.height, presentMode))
    }

    private fun precompilePipelines(device: GpuDevice) {
        val cache = PipelineCache(device, ClasspathShaderSource)
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
                cache.get(pipeline)
            } catch (e: Exception) {
                LOGGER.warn("Failed to precompile pipeline {}", pipeline.location, e)
            }
        }
        RenderSystem.setCurrentPipelineCache(cache)
    }

    private fun initFonts() {
        try {
            FontRepository.loadFont(FontRepository.DEFAULT_FONT_ID)
            val cjkFont = FontRepository.DEFAULT_FONT_ID.withPath("fonts/wqy-microhei-modified.ttf")
            FontRepository.loadFont(cjkFont)
            FontRepository.setFontSearchOrder(listOf(FontRepository.DEFAULT_FONT_ID, cjkFont))
        } catch (e: Exception) {
            LOGGER.warn("Failed to initialize fonts", e)
        }
    }
}
