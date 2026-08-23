package org.academy.internal.client.gui.imgui

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.IndexType
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.AddressMode
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import imgui.ImDrawData
import imgui.ImGui
import imgui.ImVec4
import imgui.extension.implot.ImPlot
import imgui.flag.ImGuiConfigFlags
import imgui.glfw.ImGuiImplGlfw
import imgui.type.ImInt
import org.academy.api.client.render.Render
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.lang.ref.Reference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Optional
import java.util.OptionalDouble
import java.util.concurrent.ConcurrentHashMap

/**
 * 可复用的 ImGui 后端，绑定任意 GLFW 窗口句柄（游戏内或独立桌面编辑器）。
 * 仅使用 Blaze3D 抽象层渲染（PROGRAM.md R1），不含任何 GL/VK 直调。
 *
 * @param windowHandle GLFW 窗口句柄
 * @param surfaceWidth 物理帧缓冲宽度（像素）
 * @param surfaceHeight 物理帧缓冲高度（像素）
 */
@ApiStatus.Internal
class ImGuiBackend(
    private val windowHandle: Long,
    private val surfaceWidth: () -> Int,
    private val surfaceHeight: () -> Int,
) {
    val imGuiImplGlfw = ImGuiImplGlfw()

    private var initialized = false

    private lateinit var fontTexture: GpuTexture
    private lateinit var fontTextureView: GpuTextureView
    private lateinit var fontSampler: GpuSampler
    private lateinit var vertexBuffer: GpuBuffer
    private lateinit var indexBuffer: GpuBuffer
    private lateinit var projMatrixUniform: GpuBuffer
    private var vertexBufferSize: Long = 0
    private var indexBufferSize: Long = 0
    private var fontAtlasPixels: ByteBuffer? = null
    private var fontAtlasWidth = 0
    private var fontAtlasHeight = 0

    private val projMatrixBuffer = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder())
    private val reusableClipRect = ImVec4()

    /** ImGui 纹理 ID → (view, sampler) 注册表（M11-02）：字体图集固定 ID 1，其余自 2 起。 */
    private val textures = ConcurrentHashMap<Long, Pair<GpuTextureView, GpuSampler>>()
    private val nextTextureId = java.util.concurrent.atomic.AtomicLong(2L)

    companion object {
        const val FONT_TEX_ID = 1L
    }

    /** 注册任意纹理供 ImGui 显示，返回 ImGui 纹理 ID（> 1）。 */
    fun registerTexture(view: GpuTextureView, sampler: GpuSampler): Long {
        val id = nextTextureId.getAndIncrement()
        textures[id] = Pair(view, sampler)
        return id
    }

    fun unregisterTexture(id: Long) {
        textures.remove(id)
    }

    fun init() {
        if (initialized) return
        ImGui.createContext()
        ImPlot.createContext()

        val io = ImGui.getIO()
        io.fontGlobalScale = 1f
        io.configFlags = ImGuiConfigFlags.DockingEnable or ImGuiConfigFlags.NavEnableKeyboard
        loadLocalizedFont()

        imGuiImplGlfw.init(windowHandle, true)
        initialized = true
    }

    private fun loadLocalizedFont() {
        val logger = org.academy.AcademyCraft.getLogger()
        val fonts = ImGui.getIO().fonts
        val fontResource = ImGuiBackend::class.java
            .getResourceAsStream("/assets/academy/fonts/wqy-microhei-modified.ttf")
        if (fontResource == null) {
            logger.warn("[ImGui] Localized font resource is missing; using the built-in font")
            fonts.addFontDefault()
            cacheFontAtlas()
            return
        }

        val temporaryFont = try {
            Files.createTempFile("academy-imgui-font-", ".ttf")
        } catch (exception: IOException) {
            fontResource.close()
            logger.error("[ImGui] Failed to create a temporary localized font; using the built-in font", exception)
            fonts.addFontDefault()
            cacheFontAtlas()
            return
        }
        try {
            try {
                fontResource.use {
                    Files.copy(it, temporaryFont, StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (exception: IOException) {
                logger.error("[ImGui] Failed to stage the localized font; using the built-in font", exception)
                fonts.addFontDefault()
                cacheFontAtlas()
                return
            }

            // 见 ImGuiUtilInternal 原实现：文件字体避免 JNI 数组地址失效问题。
            val glyphRanges = fonts.glyphRangesChineseSimplifiedCommon
            fonts.addFontFromFileTTF(temporaryFont.toString(), 14f, glyphRanges)
            cacheFontAtlas()
            Reference.reachabilityFence(glyphRanges)
        } finally {
            try {
                Files.deleteIfExists(temporaryFont)
            } catch (exception: IOException) {
                logger.warn("[ImGui] Failed to delete temporary font {}", temporaryFont, exception)
            }
        }
    }

    private fun cacheFontAtlas() {
        val fonts = ImGui.getIO().fonts
        val width = ImInt()
        val height = ImInt()
        val pixels = fonts.getTexDataAsRGBA32(width, height)
        val atlasWidth = width.get()
        val atlasHeight = height.get()
        val expectedSize = Math.multiplyExact(Math.multiplyExact(atlasWidth, atlasHeight), 4)

        require(atlasWidth > 0 && atlasHeight > 0 && pixels.capacity() >= expectedSize) {
            "ImGui produced an invalid font atlas: ${atlasWidth}x$atlasHeight, ${pixels.capacity()} bytes"
        }

        fontAtlasPixels = pixels
        fontAtlasWidth = atlasWidth
        fontAtlasHeight = atlasHeight

        fonts.clearInputData()
        org.academy.AcademyCraft.getLogger().info(
            "[ImGui] Prepared font atlas {}x{}",
            atlasWidth,
            atlasHeight
        )
    }

    fun render(renderTarget: RenderTarget, renderCommand: () -> Unit) {
        val colorTextureView = renderTarget.getColorTextureView() ?: return
        val device = RenderSystem.getDevice()

        newFrame()

        imGuiImplGlfw.newFrame()
        ImGui.newFrame()
        renderCommand()
        ImGui.render()

        val drawData = ImGui.getDrawData()
        if (drawData.cmdListsCount <= 0) return

        val encoder = device.createCommandEncoder()
        try {
            uploadDrawData(drawData, encoder)

            val renderPass = encoder.createRenderPass({ "ImGui" }, colorTextureView, Optional.empty())
            renderPass.use {
                renderDrawData(drawData, it)
            }
        } finally {
            encoder.submit()
        }
    }

    private fun newFrame() {
        if (!::projMatrixUniform.isInitialized) createUniform()
        if (!::fontTexture.isInitialized) createFontsTexture()
    }

    private fun createUniform() {
        val device = RenderSystem.getDevice()

        if (::projMatrixUniform.isInitialized) projMatrixUniform.close()
        projMatrixUniform = device.createBuffer(
            { "ImGui ProjMtx" },
            GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST,
            64
        )
    }

    private fun createFontsTexture() {
        val device = RenderSystem.getDevice()
        val fontAtlas = ImGui.getIO().fonts
        val pixels = checkNotNull(fontAtlasPixels) { "ImGui font atlas was not prepared during initialization" }
            .duplicate()
        val expectedSize = Math.multiplyExact(Math.multiplyExact(fontAtlasWidth, fontAtlasHeight), 4)
        pixels.clear()
        pixels.limit(expectedSize)

        disposeFontResources()

        val texture = device.createTexture(
            { "ImGui Font" },
            GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_COPY_DST,
            GpuFormat.RGBA8_UNORM,
            fontAtlasWidth, fontAtlasHeight, 1, 1
        )
        fontTexture = texture
        fontTextureView = device.createTextureView(texture)
        fontSampler = device.createSampler(
            AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
            FilterMode.LINEAR, FilterMode.LINEAR,
            1, OptionalDouble.empty()
        )

        val encoder = device.createCommandEncoder()
        encoder.writeToTexture(texture, pixels, 0, 0, 0, 0, fontAtlasWidth, fontAtlasHeight)
        encoder.submit()

        fontAtlas.setTexID(1)
    }

    private fun disposeFontResources() {
        if (::fontSampler.isInitialized) fontSampler.close()
        if (::fontTextureView.isInitialized) fontTextureView.close()
        if (::fontTexture.isInitialized) fontTexture.close()
    }

    private fun ensureVertexBuffer(requiredSize: Long): GpuBuffer {
        if (!::vertexBuffer.isInitialized || vertexBufferSize < requiredSize) {
            if (::vertexBuffer.isInitialized) vertexBuffer.close()
            vertexBufferSize = requiredSize + 4096
            vertexBuffer = RenderSystem.getDevice().createBuffer(
                { "ImGui VB" },
                GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST,
                vertexBufferSize
            )
        }
        return vertexBuffer
    }

    private fun ensureIndexBuffer(requiredSize: Long): GpuBuffer {
        if (!::indexBuffer.isInitialized || indexBufferSize < requiredSize) {
            if (::indexBuffer.isInitialized) indexBuffer.close()
            indexBufferSize = requiredSize + 1024
            indexBuffer = RenderSystem.getDevice().createBuffer(
                { "ImGui IB" },
                GpuBuffer.USAGE_INDEX or GpuBuffer.USAGE_COPY_DST,
                indexBufferSize
            )
        }
        return indexBuffer
    }

    private fun uploadDrawData(drawData: ImDrawData, encoder: CommandEncoder) {
        val fbWidth = (drawData.displaySizeX * drawData.framebufferScaleX).toInt()
        val fbHeight = (drawData.displaySizeY * drawData.framebufferScaleY).toInt()
        if (fbWidth <= 0 || fbHeight <= 0) return

        var totalVertexSize = 0L
        var totalIndexSize = 0L
        for (n in 0 until drawData.cmdListsCount) {
            totalVertexSize += drawData.getCmdListVtxBufferSize(n).toLong() * ImDrawData.sizeOfImDrawVert()
            totalIndexSize += drawData.getCmdListIdxBufferSize(n).toLong() * ImDrawData.sizeOfImDrawIdx()
        }

        val vb = ensureVertexBuffer(totalVertexSize)
        val ib = ensureIndexBuffer(totalIndexSize)

        var vertexOffset = 0L
        var indexOffset = 0L
        for (n in 0 until drawData.cmdListsCount) {
            val vtxCount = drawData.getCmdListVtxBufferSize(n)
            val idxCount = drawData.getCmdListIdxBufferSize(n)

            if (vtxCount > 0) {
                val vtxData = drawData.getCmdListVtxBufferData(n)
                val vtxSize = vtxCount * ImDrawData.sizeOfImDrawVert()
                encoder.writeToBuffer(vb.slice(vertexOffset, vtxSize.toLong()), vtxData)
                vertexOffset += vtxSize
            }

            if (idxCount > 0) {
                val idxData = drawData.getCmdListIdxBufferData(n)
                val idxSize = idxCount * ImDrawData.sizeOfImDrawIdx()
                encoder.writeToBuffer(ib.slice(indexOffset, idxSize.toLong()), idxData)
                indexOffset += idxSize
            }
        }

        val l = drawData.displayPosX
        val r = drawData.displayPosX + drawData.displaySizeX
        val t = drawData.displayPosY
        val b = drawData.displayPosY + drawData.displaySizeY

        projMatrixBuffer.clear()
        projMatrixBuffer.putFloat(2.0f / (r - l)).putFloat(0f).putFloat(0f).putFloat(0f)
        projMatrixBuffer.putFloat(0f).putFloat(2.0f / (t - b)).putFloat(0f).putFloat(0f)
        projMatrixBuffer.putFloat(0f).putFloat(0f).putFloat(-1f).putFloat(0f)
        projMatrixBuffer.putFloat((r + l) / (l - r)).putFloat((t + b) / (b - t)).putFloat(0f).putFloat(1f)
        projMatrixBuffer.flip()

        encoder.writeToBuffer(projMatrixUniform.slice(), projMatrixBuffer)
    }

    private fun renderDrawData(drawData: ImDrawData, renderPass: RenderPass) {
        val fbWidth = (drawData.displaySizeX * drawData.framebufferScaleX).toInt()
        val fbHeight = (drawData.displaySizeY * drawData.framebufferScaleY).toInt()
        if (fbWidth <= 0 || fbHeight <= 0) return

        val vb = vertexBuffer
        val ib = indexBuffer
        val projMtx = projMatrixUniform
        val fontView = fontTextureView
        val fontSampler = this.fontSampler

        renderPass.setPipeline(Render.RenderPipelines.IMGUI)
        renderPass.setUniform("Projection", projMtx)

        val clipOffX = drawData.displayPosX
        val clipOffY = drawData.displayPosY
        val clipScaleX = drawData.framebufferScaleX
        val clipScaleY = drawData.framebufferScaleY

        val indexType = if (ImDrawData.sizeOfImDrawIdx() == 2) IndexType.SHORT else IndexType.INT

        var vertexOffset = 0L
        var indexOffset = 0L

        val physicalWidth = surfaceWidth()
        val physicalHeight = surfaceHeight()

        for (n in 0 until drawData.cmdListsCount) {
            val vtxCount = drawData.getCmdListVtxBufferSize(n)
            val idxCount = drawData.getCmdListIdxBufferSize(n)
            val vtxSize = vtxCount * ImDrawData.sizeOfImDrawVert()
            val idxSize = idxCount * ImDrawData.sizeOfImDrawIdx()

            if (vtxCount == 0 || idxCount == 0) continue

            renderPass.setVertexBuffer(0, vb.slice(vertexOffset, vtxSize.toLong()))
            renderPass.setIndexBuffer(ib, indexType)

            for (cmdI in 0 until drawData.getCmdListCmdBufferSize(n)) {
                drawData.getCmdListCmdBufferClipRect(reusableClipRect, n, cmdI)

                val clipMinX = (reusableClipRect.x - clipOffX) * clipScaleX
                val clipMinY = (reusableClipRect.y - clipOffY) * clipScaleY
                val clipMaxX = (reusableClipRect.z - clipOffX) * clipScaleX
                val clipMaxY = (reusableClipRect.w - clipOffY) * clipScaleY

                if (clipMaxX <= clipMinX || clipMaxY <= clipMinY) continue

                val scissorX = clipMinX.toInt()
                val scissorY = (fbHeight - clipMaxY).toInt()
                val scissorW = (clipMaxX - clipMinX).toInt()
                val scissorH = (clipMaxY - clipMinY).toInt()

                if (scissorW > 0 && scissorH > 0) {
                    val clampedX = scissorX.coerceIn(0, physicalWidth)
                    val clampedY = scissorY.coerceIn(0, physicalHeight)
                    val clampedRight = (scissorX + scissorW).coerceIn(0, physicalWidth)
                    val clampedBottom = (scissorY + scissorH).coerceIn(0, physicalHeight)
                    val clampedWidth = clampedRight - clampedX
                    val clampedHeight = clampedBottom - clampedY
                    if (clampedWidth > 0 && clampedHeight > 0) {
                        renderPass.enableScissor(clampedX, clampedY, clampedWidth, clampedHeight)
                    }
                }

                // 逐命令绑定纹理（M11-02）：字体图集 ID 1，其余查注册表
                val texId = drawData.getCmdListCmdBufferTextureId(n, cmdI)
                val binding = if (texId == FONT_TEX_ID) Pair(fontView, fontSampler) else textures[texId]
                if (binding == null) continue
                renderPass.bindTexture("Texture", binding.first, binding.second)

                val elemCount = drawData.getCmdListCmdBufferElemCount(n, cmdI)
                val idxBufferOffset = drawData.getCmdListCmdBufferIdxOffset(n, cmdI)
                val vtxBufferOffset = drawData.getCmdListCmdBufferVtxOffset(n, cmdI)

                val firstIndex = (indexOffset / ImDrawData.sizeOfImDrawIdx()).toInt() + idxBufferOffset

                renderPass.drawIndexed(elemCount, 1, firstIndex, vtxBufferOffset, 0)
            }

            vertexOffset += vtxSize
            indexOffset += idxSize
        }
    }

    fun clearEventsQueue() {
        ImGui.getIO().clearEventsQueue()
    }

    fun wantCaptureMouse(): Boolean = ImGui.getIO().wantCaptureMouse

    fun wantCaptureKeyboard(): Boolean = ImGui.getIO().wantCaptureKeyboard

    fun dispose() {
        if (!initialized) return
        if (::vertexBuffer.isInitialized) vertexBuffer.close()
        if (::indexBuffer.isInitialized) indexBuffer.close()
        if (::projMatrixUniform.isInitialized) projMatrixUniform.close()
        disposeFontResources()

        vertexBufferSize = 0
        indexBufferSize = 0
        fontAtlasPixels = null
        fontAtlasWidth = 0
        fontAtlasHeight = 0
        textures.clear()

        imGuiImplGlfw.shutdown()
        ImPlot.destroyContext()
        ImGui.destroyContext()
        initialized = false
    }
}
