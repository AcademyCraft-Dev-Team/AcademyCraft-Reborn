package org.academy.internal.client.gui.imgui

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.IndexType
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.*
import imgui.ImDrawData
import imgui.ImGui
import imgui.ImVec4
import imgui.extension.implot.ImPlot
import imgui.flag.ImGuiConfigFlags
import imgui.glfw.ImGuiImplGlfw
import imgui.type.ImInt
import net.minecraft.client.Minecraft
import org.academy.api.client.render.Render
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.lang.ref.Reference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*

@ApiStatus.Internal
object ImGuiUtilInternal {
    val imGuiImplGlfw = ImGuiImplGlfw()

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

    fun init() {
        val handle = Minecraft.getInstance().window.handle()
        ImGui.createContext()
        ImPlot.createContext()

        val io = ImGui.getIO()
        io.fontGlobalScale = 1f
        io.configFlags = ImGuiConfigFlags.DockingEnable or ImGuiConfigFlags.NavEnableKeyboard
        loadLocalizedFont()

        imGuiImplGlfw.init(handle, true)
    }

    private fun loadLocalizedFont() {
        val logger = org.academy.AcademyCraft.getLogger()
        val fonts = ImGui.getIO().fonts
        val fontResource = ImGuiUtilInternal::class.java
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

            // imgui-java's memory-font overload exposes Java array addresses to native code.
            // Those addresses are no longer valid after JNI releases the arrays, while ImGui
            // keeps them for deferred atlas construction. A file font gives ImGui native-owned
            // font data; building immediately keeps the remaining glyph-range pointer scoped to
            // this initialization transaction instead of the first GUI render minutes later.
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

        // The custom renderer uses the legacy, fully-built atlas path. Once pixels have been
        // copied into Java-owned direct memory, native font inputs must not retain JNI pointers.
        fonts.clearInputData()
        org.academy.AcademyCraft.getLogger().info(
            "[ImGui] Prepared font atlas {}x{} during client initialization",
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
        val texView = fontTextureView
        val sampler = fontSampler

        renderPass.setPipeline(Render.RenderPipelines.IMGUI)
        renderPass.setUniform("Projection", projMtx)
        renderPass.bindTexture("Texture", texView, sampler)

        val clipOffX = drawData.displayPosX
        val clipOffY = drawData.displayPosY
        val clipScaleX = drawData.framebufferScaleX
        val clipScaleY = drawData.framebufferScaleY

        val indexType = if (ImDrawData.sizeOfImDrawIdx() == 2) IndexType.SHORT else IndexType.INT

        var vertexOffset = 0L
        var indexOffset = 0L

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
                    val physicalWidth = Minecraft.getInstance().window.width
                    val physicalHeight = Minecraft.getInstance().window.height
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
        if (::vertexBuffer.isInitialized) vertexBuffer.close()
        if (::indexBuffer.isInitialized) indexBuffer.close()
        if (::projMatrixUniform.isInitialized) projMatrixUniform.close()
        disposeFontResources()

        vertexBufferSize = 0
        indexBufferSize = 0
        fontAtlasPixels = null
        fontAtlasWidth = 0
        fontAtlasHeight = 0

        imGuiImplGlfw.shutdown()
        ImPlot.destroyContext()
        ImGui.destroyContext()
    }
}
