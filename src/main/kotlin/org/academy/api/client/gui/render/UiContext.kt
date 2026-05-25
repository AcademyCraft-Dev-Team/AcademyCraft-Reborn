package org.academy.api.client.gui.render

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.TextureFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.DynamicUniformStorage
import net.minecraft.client.renderer.DynamicUniformStorage.DynamicUniform
import net.minecraft.client.renderer.Projection
import net.minecraft.client.renderer.ProjectionMatrixBuffer
import org.academy.api.client.gui.command.SubmittedCommand
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.render.BatchProcessor.UboUploader
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.api.client.render.UniformPayload
import org.academy.api.client.thread.MainThread
import org.academy.api.client.thread.RenderThread
import org.academy.api.client.util.ClientUtil
import org.academy.api.common.util.UncheckedUtil
import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

open class UiContext(private val layered: Float = 3000f) {
    private val commandList = AtomicReference<MutableList<SubmittedCommand>?>()

    private val closed = AtomicBoolean(false)
    private val closing = AtomicBoolean(false)

    private val dynamicUniformStorages: MutableMap<Class<out DynamicUniform>, DynamicUniformStorage<*>> =
        HashMap()
    private val commandExecutor = CommandExecutor()

    private var projectionMatrixBuffer: ProjectionMatrixBuffer? = null
    private val projection = Projection()
    private var dynamicTransformsUbo: GpuBuffer? = null

    init {
        ClientUtil.getRenderEventLoop().execute { this.initOnRenderThread() }
    }

    @MainThread
    fun perform(rootWidget: WidgetContainer, mouseX: Double, mouseY: Double, partialTick: Float) {
        if (closed.get() || closing.get()) return

        val window = Minecraft.getInstance().window

        val width = window.guiScaledWidth
        val height = window.guiScaledHeight

        val widthSpec = MeasureSpec(MeasureSpec.Mode.EXACTLY, width.toFloat())
        val heightSpec = MeasureSpec(MeasureSpec.Mode.EXACTLY, height.toFloat())

        if (rootWidget.isLayoutDirty) {
            rootWidget.measure(widthSpec, heightSpec)
            rootWidget.layout(0f, 0f, width.toFloat(), height.toFloat())
        }

        val context = RenderContext()
        generateCommands(context, rootWidget, mouseX, mouseY, partialTick)
        commandList.set(context.commands)
    }

    @RenderThread
    fun upload(target: RenderTarget, clear: Boolean) {
        for (ubo in dynamicUniformStorages.values) ubo.endFrame()

        val commandEncoder = RenderSystem.getDevice().createCommandEncoder()
        val colorTexture = target.getColorTexture()
        val depthTextureView = target.getDepthTextureView() ?: return
        val depthTexture = depthTextureView.texture()
        val colorTextureView = target.getColorTextureView()

        if (colorTexture == null || colorTextureView == null) return

        if (clear) commandEncoder.clearColorAndDepthTextures(colorTexture, 0, depthTexture, 1.0)

        val projectionMatrixBuffer = projectionMatrixBuffer
        if (projectionMatrixBuffer == null || dynamicTransformsUbo == null) return

        val commands = commandList.getAndSet(null)

        if (commands.isNullOrEmpty()) return

        val depthEpsilon: Float = calculateDepthEpsilon(depthTexture) * layered

        val meshesToDraw = BatchProcessor.process(
            commands,
            depthEpsilon,
            object : UboUploader {
                override fun <T : DynamicUniform> upload(payload: UniformPayload<T>): GpuBufferSlice {
                    return uploadPayload(payload)
                }
            })

        val effectiveScale = Minecraft.getInstance().window.guiScale.toFloat()
        val window = Minecraft.getInstance().window
        val guiScaledWidth = window.width / effectiveScale
        val guiScaledHeight = window.height / effectiveScale
        /*
         * Map: z [0, layered] -> NDC [1, -1] :: Depth [1, 0]
         * Eq : ndc = z * 2 / (zNear - zFar) + (zNear + zFar) / (zNear - zFar)
         * z=0 => ndc= 1 => (zNear + zFar) / (zNear - zFar) = 1 => zFar = 0
         * z=layered => ndc=-1 => layered * 2 / zNear + 1 = -1 => zNear = -layered
         */
        projection.setupOrtho(-layered, 0.0f, guiScaledWidth, guiScaledHeight, true)
        val projectionBufferSlice = projectionMatrixBuffer.getBuffer(projection)
        commandExecutor.execute(
            meshesToDraw, colorTextureView, depthTextureView,
            projectionBufferSlice, dynamicTransformsUbo!!, effectiveScale
        )
    }

    open fun generateCommands(
        context: RenderContext, rootWidget: WidgetContainer, mouseX: Double, mouseY: Double, partialTick: Float
    ) {
        context.pose().pushPose()
        run {
            context.pose().translate(rootWidget.x, rootWidget.y, rootWidget.z)
            context.pose().translate(rootWidget.translationX, rootWidget.translationY, 0f)
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
        ClientUtil.getRenderEventLoop().execute { this.closeOnRenderThread() }
    }

    fun closeOnRenderThread() {
        if (projectionMatrixBuffer != null) projectionMatrixBuffer!!.close()
        if (dynamicTransformsUbo != null) dynamicTransformsUbo!!.close()

        commandExecutor.close()
        for (ubo in dynamicUniformStorages.values) ubo.close()
        dynamicUniformStorages.clear()
        closed.set(true)
        closing.set(false)
    }

    companion object {
        private fun calculateDepthEpsilon(depthTexture: GpuTexture): Float {
            val format = depthTexture.format

            if (!format.hasDepthAspect()) return 0f

            val depthBits = when (format) {
                TextureFormat.DEPTH32, TextureFormat.DEPTH32_STENCIL8 -> 32
                TextureFormat.DEPTH24_STENCIL8 -> 24
                else -> 0
            }

            if (depthBits == 0) return 0f

            return 1f / ((1L shl depthBits) - 1)
        }
    }
}