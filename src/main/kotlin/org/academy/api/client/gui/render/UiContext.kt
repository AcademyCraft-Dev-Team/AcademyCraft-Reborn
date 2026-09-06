package org.academy.api.client.gui.render

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.render.GuiItemAtlas
import net.minecraft.client.renderer.DynamicUniformStorage
import net.minecraft.client.renderer.DynamicUniformStorage.DynamicUniform
import net.minecraft.client.renderer.Projection
import net.minecraft.client.renderer.ProjectionMatrixBuffer
import org.academy.api.client.gui.command.ItemStackDrawCommand
import org.academy.api.client.gui.command.SubmittedCommand
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.render.BatchProcessor.UboUploader
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.api.client.render.UniformPayload
import org.academy.api.client.thread.MainThread
import org.academy.api.client.thread.RenderThread
import org.academy.api.common.util.UncheckedUtil
import org.joml.Matrix4f
import org.joml.Vector4f
import org.lwjgl.system.MemoryStack
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil

open class UiContext {
    private val commandList = AtomicReference<MutableList<SubmittedCommand>?>()

    /** 主线程 [perform] 写入, 渲染线程 [blurRegions] 读取喵. */
    @Volatile
    private var lastBlurRegions: List<BlurRegion> = emptyList()

    /** 上次 [perform] 的根控件; 换 screen 时作废全部缓存喵. */
    private var performedRoot: WidgetContainer? = null

    private val closed = AtomicBoolean(false)
    private val closing = AtomicBoolean(false)

    private val dynamicUniformStorages: MutableMap<Class<out DynamicUniform>, DynamicUniformStorage<*>> =
        HashMap()
    private val commandExecutor = CommandExecutor()
    private var itemAtlas: GuiItemAtlas? = null
    private var itemAtlasSlotSize = 0

    private var projectionMatrixBuffer: ProjectionMatrixBuffer? = null
    private val projection = Projection()
    private var dynamicTransformsUbo: GpuBuffer? = null

    init {
        UiEnvironment.get().runOnMainThread { this.initOnRenderThread() }
    }

    @MainThread
    fun perform(rootWidget: WidgetContainer, mouseX: Double, mouseY: Double, partialTick: Float) {
        val env = UiEnvironment.get()
        perform(rootWidget, mouseX, mouseY, partialTick, env.guiScaledWidth.toFloat(), env.guiScaledHeight.toFloat())
    }

    @MainThread
    fun perform(
        rootWidget: WidgetContainer,
        mouseX: Double,
        mouseY: Double,
        partialTick: Float,
        logicalW: Float,
        logicalH: Float
    ) {
        if (closed.get() || closing.get()) return

        if (performedRoot !== rootWidget) {
            commandList.set(null)
            lastBlurRegions = emptyList()
            performedRoot = rootWidget
        }

        val width = logicalW
        val height = logicalH

        val widthSpec = MeasureSpec(MeasureSpec.Mode.EXACTLY, width)
        val heightSpec = MeasureSpec(MeasureSpec.Mode.EXACTLY, height)

        if (rootWidget.isLayoutDirty) {
            rootWidget.measure(widthSpec, heightSpec)
            rootWidget.layout(0f, 0f, width, height)
        }

        val context = RenderContext()
        generateCommands(context, rootWidget, mouseX, mouseY, partialTick)
        commandList.set(context.commands)
        lastBlurRegions = context.blurRegions
    }

    /** 本帧收集到的模糊区域，供宿主交给 [UiCompositor]（需要世界/下方两个 target）。 */
    fun blurRegions(): List<BlurRegion> = lastBlurRegions

    /**
     * 将缓存的命令+模糊区域切分为多个段, 供外部逐层合成喵.
     * 返回 null 表示无命令.
     */
    @RenderThread
    fun splitSegments(
        blurRegions: List<BlurRegion>
    ): Pair<List<SubmittedCommand>, List<Pair<List<SubmittedCommand>, List<BlurRegion>>>>? {
        val env = UiEnvironment.get()
        val commands = commandList.getAndSet(null) ?: return null
        if (commands.isEmpty()) return null
        val preparedCommands = prepareItemCommands(commands, env.guiScale)
        for (ubo in dynamicUniformStorages.values) ubo.endFrame()
        val segments = splitCommandsForBlurByIndex(preparedCommands, blurRegions)
        return preparedCommands to segments
    }

    /**
     * 将命令渲染到 [target] 喵.
     */
    @RenderThread
    fun drawCommands(
        target: RenderTarget,
        commands: List<SubmittedCommand>,
        clear: Boolean,
        guiScaledW: Float,
        guiScaledH: Float
    ) {
        val env = UiEnvironment.get()
        val effectiveScale = env.guiScale
        drawPrepared(target, commands.toMutableList(), clear, guiScaledW, guiScaledH, effectiveScale)
    }

    @RenderThread
    fun upload(target: RenderTarget, clear: Boolean) {
        val env = UiEnvironment.get()
        upload(target, clear, env.physicalWidth / env.guiScale, env.physicalHeight / env.guiScale)
    }

    @RenderThread
    fun upload(target: RenderTarget, clear: Boolean, guiScaledW: Float, guiScaledH: Float) {
        uploadSplit(target, clear, guiScaledW, guiScaledH, aboveTarget = null, blurRegions = emptyList())
    }

    /**
     * 上传命令到 [target]. 当 [blurRegions] 非空且提供 [aboveTarget] 时, 以模糊区域的
     * [BlurRegion.commandIndex] 为界把命令列表切为多个段, 逐层渲染→模糊→合成喵.
     * 否则单 pass.
     */
    @RenderThread
    fun uploadSplit(
        target: RenderTarget,
        clear: Boolean,
        guiScaledW: Float,
        guiScaledH: Float,
        aboveTarget: RenderTarget?,
        blurRegions: List<BlurRegion>,
        backdropBlur: org.academy.api.client.render.post.BackdropBlurEngine? = null
    ) {
        for (ubo in dynamicUniformStorages.values) ubo.endFrame()

        val environment = UiEnvironment.get()
        val effectiveScale = environment.guiScale

        val commands = commandList.getAndSet(null)
        if (commands.isNullOrEmpty()) return

        val preparedCommands = prepareItemCommands(commands, effectiveScale)

        if (blurRegions.isEmpty() || aboveTarget == null) {
            drawPrepared(target, preparedCommands, clear, guiScaledW, guiScaledH, effectiveScale)
        } else {
            val segments = splitCommandsForBlurByIndex(preparedCommands, blurRegions)
            if (backdropBlur != null) {
                drawSegments(target, aboveTarget, segments, clear, guiScaledW, guiScaledH, effectiveScale, backdropBlur)
            } else {
                drawPrepared(target, preparedCommands, clear, guiScaledW, guiScaledH, effectiveScale)
            }
        }
    }

    @RenderThread
    private fun drawSegments(
        target: RenderTarget,
        aboveTarget: RenderTarget,
        segments: List<Pair<MutableList<SubmittedCommand>, List<BlurRegion>>>,
        clear: Boolean,
        guiScaledW: Float,
        guiScaledH: Float,
        effectiveScale: Float,
        backdropBlur: org.academy.api.client.render.post.BackdropBlurEngine
    ) {
        if (segments.isEmpty()) return

        val targetView = target.getColorTextureView() ?: return
        val aboveView = aboveTarget.getColorTextureView() ?: return
        var isFirst = true

        for ((segmentCommands, segmentRegions) in segments) {
            if (segmentCommands.isEmpty() && segmentRegions.isEmpty()) continue

            if (segmentCommands.isNotEmpty()) {
                if (isFirst) {
                    drawPrepared(target, segmentCommands, clear, guiScaledW, guiScaledH, effectiveScale)
                    isFirst = false
                } else {
                    drawPrepared(aboveTarget, segmentCommands, true, guiScaledW, guiScaledH, effectiveScale)
                    UiCompositor.blitSource(targetView, aboveView)
                }
            }

            if (segmentRegions.isNotEmpty()) {
                backdropBlur.capture(targetView, segmentRegions.maxOf { it.radius })
                for (region in segmentRegions) {
                    backdropBlur.fillRegion(
                        targetView,
                        region.x, region.y, region.width, region.height,
                        region.radius,
                        UiCompositor.NEUTRAL_TINT
                    )
                }
            }
        }
    }

    @RenderThread
    private fun drawPrepared(
        target: RenderTarget,
        commands: MutableList<SubmittedCommand>,
        clear: Boolean,
        guiScaledW: Float,
        guiScaledH: Float,
        effectiveScale: Float
    ) {
        val commandEncoder = RenderSystem.getDevice().createCommandEncoder()
        val colorTexture = target.getColorTexture()
        val colorTextureView = target.getColorTextureView()

        if (colorTexture == null || colorTextureView == null) return

        if (clear) commandEncoder.clearColorTexture(colorTexture, Vector4f(0f))

        if (commands.isEmpty()) return

        val projectionMatrixBuffer = projectionMatrixBuffer
        val dynamicTransformsUbo = dynamicTransformsUbo
        if (projectionMatrixBuffer == null || dynamicTransformsUbo == null) return

        val meshesToDraw = BatchProcessor.process(
            commands,
            object : UboUploader {
                override fun <T : DynamicUniform> upload(payload: UniformPayload<T>): GpuBufferSlice {
                    return uploadPayload(payload)
                }
            })

        projection.setupOrtho(0f, 1f, guiScaledW, guiScaledH, true)
        val projectionBufferSlice = projectionMatrixBuffer.getBuffer(projection)
        commandExecutor.execute(
            meshesToDraw, colorTextureView,
            projectionBufferSlice, dynamicTransformsUbo, effectiveScale
        )
    }

    companion object {
        /**
         * 按模糊区域的 [BlurRegion.commandIndex] 将命令列表切分为多个段喵.
         * 返回 List<Pair<该段的命令, 该段需要模糊的区域>>.
         * 相邻同一 commandIndex 的区域归入同一段.
         */
        fun splitCommandsForBlurByIndex(
            commands: List<SubmittedCommand>,
            regions: List<BlurRegion>
        ): List<Pair<MutableList<SubmittedCommand>, List<BlurRegion>>> {
            if (regions.isEmpty()) return listOf(commands.toMutableList() to emptyList())

            val sortedRegions = regions.sortedBy { it.commandIndex }
            val result = mutableListOf<Pair<MutableList<SubmittedCommand>, List<BlurRegion>>>()
            var prevIndex = 0
            var i = 0

            while (i < sortedRegions.size) {
                val region = sortedRegions[i]
                val idx = region.commandIndex

                if (idx > prevIndex) {
                    result.add(commands.subList(prevIndex, idx).toMutableList() to emptyList())
                }

                val sameIndex = mutableListOf<BlurRegion>()
                while (i < sortedRegions.size && sortedRegions[i].commandIndex == idx) {
                    sameIndex.add(sortedRegions[i])
                    i++
                }
                result.add(mutableListOf<SubmittedCommand>() to sameIndex)
                prevIndex = idx
            }

            if (prevIndex < commands.size) {
                result.add(commands.subList(prevIndex, commands.size).toMutableList() to emptyList())
            }

            return result
        }
    }

    @RenderThread
    private fun prepareItemCommands(
        commands: MutableList<SubmittedCommand>,
        effectiveScale: Float
    ): MutableList<SubmittedCommand> {
        val itemCommands = commands.mapNotNull { it.command as? ItemStackDrawCommand }
        if (itemCommands.isEmpty()) {
            itemAtlas?.endFrame()
            return commands
        }

        val slotSize = ceil(16f * effectiveScale).toInt().coerceAtLeast(16)
        val identities = itemCommands.mapTo(mutableSetOf()) { it.itemState.modelIdentity }
        val textureSize = GuiItemAtlas.computeTextureSizeFor(slotSize, identities.size)

        var atlas = itemAtlas
        val canReuse = atlas != null &&
                itemAtlasSlotSize == slotSize &&
                atlas.textureSize() == textureSize &&
                atlas.tryPrepareFor(identities)
        if (!canReuse) {
            atlas?.close()
            atlas = GuiItemAtlas(
                Minecraft.getInstance().gameRenderer.featureRenderDispatcher(),
                textureSize,
                slotSize
            )
            itemAtlas = atlas
            itemAtlasSlotSize = slotSize
        }

        val sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)
        val prepared = commands.mapNotNullTo(mutableListOf()) { submitted ->
            val itemCommand = submitted.command as? ItemStackDrawCommand ?: return@mapNotNullTo submitted
            val slot = atlas.getOrUpdate(itemCommand.itemState) ?: return@mapNotNullTo null
            SubmittedCommand(
                itemCommand.resolve(slot, sampler),
                submitted.pose,
                submitted.scissorRect,
                submitted.drawOrder,
                submitted.commandIndex,
                submitted.alphaMul
            )
        }
        atlas.endFrame()
        return prepared
    }

    open fun generateCommands(
        context: RenderContext, rootWidget: WidgetContainer, mouseX: Double, mouseY: Double, partialTick: Float
    ) {
        context.pose().pushPose()
        run {
            context.pose().translate(rootWidget.x, rootWidget.y)
            context.pose().translate(rootWidget.translationX, rootWidget.translationY)
            rootWidget.render(context)
        }
        context.pose().popPose()
    }

    private fun <T : DynamicUniform> uploadPayload(
        payload: UniformPayload<T>
    ): GpuBufferSlice {
        return getOrCreateUbo(payload.type, payload.size).writeUniform(payload.data)
    }

    @MainThread
    private fun <T : DynamicUniform> getOrCreateUbo(
        uboClass: Class<T>, size: Int
    ): DynamicUniformStorage<T> {
        return UncheckedUtil.uncheckedCast<DynamicUniformStorage<T>>(
            dynamicUniformStorages.computeIfAbsent(
                uboClass
            ) { _ ->
                DynamicUniformStorage<DynamicUniform>(
                    uboClass.getSimpleName() + "_UBO", size, 2
                )
            })
    }

    @RenderThread
    private fun initOnRenderThread() {
        projectionMatrixBuffer = ProjectionMatrixBuffer("ac_ui")
        val device = RenderSystem.getDevice()
        val uboUsage = GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST

        MemoryStack.stackPush().use { memoryStack ->
            val size = Std140SizeCalculator().putMat4f().putVec4().putVec3().putMat4f().putFloat().get()
            val builder = Std140Builder.onStack(memoryStack, size)
            val identityMatrix = Matrix4f()
            builder.putMat4f(identityMatrix)
            builder.putVec4(1.0f, 1.0f, 1.0f, 1.0f)
            builder.putVec3(0.0f, 0.0f, 0.0f)
            builder.putMat4f(identityMatrix)
            builder.putFloat(1.0f)
            val byteBuffer = builder.get()
            dynamicTransformsUbo = device.createBuffer(
                { "UI DynamicTransforms UBO" }, uboUsage, byteBuffer
            )
        }
    }

    fun close() {
        if (closing.get() || closed.get()) return
        closing.set(true)
        UiEnvironment.get().runOnMainThread { this.closeOnRenderThread() }
    }

    fun closeOnRenderThread() {
        if (projectionMatrixBuffer != null) projectionMatrixBuffer!!.close()
        if (dynamicTransformsUbo != null) dynamicTransformsUbo!!.close()

        commandExecutor.close()
        itemAtlas?.close()
        itemAtlas = null
        itemAtlasSlotSize = 0
        for (ubo in dynamicUniformStorages.values) ubo.close()
        dynamicUniformStorages.clear()
        lastBlurRegions = emptyList()
        performedRoot = null
        closed.set(true)
        closing.set(false)
    }
}
