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
import org.academy.AcademyCraft
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
    private var cachedCommands: MutableList<SubmittedCommand>? = null

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
            cachedCommands = null
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

        if (shouldUseCacheCommands(rootWidget)) {
            commandList.set(cachedCommands)
            return
        }

        val context = RenderContext()
        generateCommands(context, rootWidget, mouseX, mouseY, partialTick)
        rootWidget.isRenderDirty = false
        cachedCommands = context.commands.toMutableList()
        commandList.set(context.commands)
        lastBlurRegions = context.blurRegions
    }

    open fun shouldUseCacheCommands(rootWidget: WidgetContainer): Boolean {
        return !AcademyCraft.DEBUG_UI && !rootWidget.hasPendingRender() && cachedCommands != null
    }

    /** 本帧收集到的模糊区域，供宿主交给 [UiCompositor]（需要世界/下方两个 target）。 */
    fun blurRegions(): List<BlurRegion> = lastBlurRegions

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
     * 上传命令到 [target]. 当 [blurRegions] 非空且提供 [aboveTarget] 时, 以模糊区域为界把命令
     * 切为两层: 「下方内容」照常渲染进 [target] (经 vanilla GUI 管线叠加在原版屏幕背景上),
     * 「上方内容」渲染进 [aboveTarget] (清屏后), 由宿主在 GUI 渲染完成后就地合成. 否则单 pass.
     */
    @RenderThread
    fun uploadSplit(
        target: RenderTarget,
        clear: Boolean,
        guiScaledW: Float,
        guiScaledH: Float,
        aboveTarget: RenderTarget?,
        blurRegions: List<BlurRegion>
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
            val (below, above) = splitCommandsForBlur(preparedCommands, blurRegions)
            drawPrepared(target, below, clear, guiScaledW, guiScaledH, effectiveScale)
            drawPrepared(aboveTarget, above, true, guiScaledW, guiScaledH, effectiveScale)
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
         * 以模糊区域最小 drawOrder 为界切分命令: 返回 (below, above).
         * below = drawOrder 严格小于最小模糊顺序的命令; above = 其余.
         */
        fun splitCommandsForBlur(
            commands: List<SubmittedCommand>,
            regions: List<BlurRegion>
        ): Pair<MutableList<SubmittedCommand>, MutableList<SubmittedCommand>> {
            val minOrder = regions.minOfOrNull { it.drawOrder }
                ?: return commands.toMutableList() to mutableListOf()
            val below = commands.filterTo(ArrayList()) { it.drawOrder < minOrder }
            val above = commands.filterTo(ArrayList()) { it.drawOrder >= minOrder }
            return below to above
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
                submitted.drawOrder
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
        cachedCommands = null
        lastBlurRegions = emptyList()
        performedRoot = null
        closed.set(true)
        closing.set(false)
    }
}
