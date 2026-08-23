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
        if (closed.get() || closing.get()) return

        val environment = UiEnvironment.get()

        val width = environment.guiScaledWidth
        val height = environment.guiScaledHeight

        val widthSpec = MeasureSpec(MeasureSpec.Mode.EXACTLY, width.toFloat())
        val heightSpec = MeasureSpec(MeasureSpec.Mode.EXACTLY, height.toFloat())

        if (rootWidget.isLayoutDirty) {
            rootWidget.measure(widthSpec, heightSpec)
            rootWidget.layout(0f, 0f, width.toFloat(), height.toFloat())
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
    }

    open fun shouldUseCacheCommands(rootWidget: WidgetContainer): Boolean {
        return !AcademyCraft.DEBUG_UI && !rootWidget.hasPendingRender() && cachedCommands != null
    }

    @RenderThread
    fun upload(target: RenderTarget, clear: Boolean) {
        for (ubo in dynamicUniformStorages.values) ubo.endFrame()

        val commandEncoder = RenderSystem.getDevice().createCommandEncoder()
        val colorTexture = target.getColorTexture()
        val colorTextureView = target.getColorTextureView()

        if (colorTexture == null || colorTextureView == null) return

        if (clear) commandEncoder.clearColorTexture(colorTexture, Vector4f(0f))

        val projectionMatrixBuffer = projectionMatrixBuffer
        val dynamicTransformsUbo = dynamicTransformsUbo
        if (projectionMatrixBuffer == null || dynamicTransformsUbo == null) return

        val commands = commandList.getAndSet(null)

        if (commands.isNullOrEmpty()) return

        val environment = UiEnvironment.get()
        val effectiveScale = environment.guiScale
        val preparedCommands = prepareItemCommands(commands, effectiveScale)

        val meshesToDraw = BatchProcessor.process(
            preparedCommands,
            object : UboUploader {
                override fun <T : DynamicUniform> upload(payload: UniformPayload<T>): GpuBufferSlice {
                    return uploadPayload(payload)
                }
            })

        val guiScaledWidth = environment.physicalWidth / effectiveScale
        val guiScaledHeight = environment.physicalHeight / effectiveScale
        projection.setupOrtho(0f, 1f, guiScaledWidth, guiScaledHeight, true)
        val projectionBufferSlice = projectionMatrixBuffer.getBuffer(projection)
        commandExecutor.execute(
            meshesToDraw, colorTextureView,
            projectionBufferSlice, dynamicTransformsUbo, effectiveScale
        )
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
        closed.set(true)
        closing.set(false)
    }
}
