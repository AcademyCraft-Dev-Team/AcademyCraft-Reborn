package org.academy.api.client.gui.render

import com.mojang.blaze3d.vertex.PoseStack
import org.academy.api.client.gui.command.DrawCommand
import org.academy.api.client.gui.command.SubmittedCommand
import org.joml.Quaternionfc
import java.util.*
import java.util.function.Supplier

class RenderContext {
    val commands = mutableListOf<SubmittedCommand>()
    private val pose = PoseStack()
    private val pose2D = PoseStack2D()
    private val scissorStack: ScissorStack
    private val drawOrderStack: DrawOrderStack
    private val alphaStack: AlphaStack
    private val blurRegionBuffer = mutableListOf<BlurRegion>()

    /** 本帧收集到的模糊区域快照。 */
    val blurRegions: List<BlurRegion>
        get() = blurRegionBuffer

    var recordedMax = 0
        private set

    init {
        scissorStack = ScissorStack()
        drawOrderStack = DrawOrderStack()
        alphaStack = AlphaStack()
    }

    fun resetRecordedMax() {
        recordedMax = 0
    }

    fun submit(command: DrawCommand) {
        val currentPose = pose.last().copy()
        val currentScissor = scissorStack.peek()
        val currentDrawOrder = drawOrderStack.peek()
        if (currentDrawOrder > recordedMax) recordedMax = currentDrawOrder
        commands.add(SubmittedCommand(command, currentPose, currentScissor, currentDrawOrder))
    }

    fun addCached(cached: List<SubmittedCommand>, regions: List<BlurRegion> = emptyList()) {
        for (command in cached) {
            if (command.drawOrder > recordedMax) recordedMax = command.drawOrder
        }
        commands.addAll(cached)
        blurRegionBuffer.addAll(regions)
    }

    fun pose(): PoseStack2D {
        return pose2D
    }

    fun drawOrder(): DrawOrderStack {
        return drawOrderStack
    }

    fun alpha(): AlphaStack {
        return alphaStack
    }

    /** 登记一个屏幕空间模糊区域（[BlurPanelWidget]）。由 [UiContext] 收集并交给 [UiCompositor]。 */
    fun registerBlurRegion(region: BlurRegion) {
        blurRegionBuffer.add(region)
    }

    /** 当前已登记的模糊区域数量 (供缓存切分时取其增量)。 */
    fun blurRegionCount(): Int = blurRegionBuffer.size

    /** 从 [fromIndex] 起新登记的模糊区域 (供缓存时独立捕获子树内的区域)。 */
    fun blurRegionsSince(fromIndex: Int): List<BlurRegion> =
        if (fromIndex < blurRegionBuffer.size) blurRegionBuffer.subList(fromIndex, blurRegionBuffer.size).toList()
        else emptyList()

    fun enableScissor(scissorRect: ScissorRect) {
        scissorStack.push(scissorRect)
    }

    fun disableScissor() {
        scissorStack.pop()
    }

    val accumulatedAlpha: Float
        get() = alphaStack.peek()

    fun clear() {
        commands.clear()
        drawOrderStack.clear()
        alphaStack.clear()
        blurRegionBuffer.clear()
    }

    inner class PoseStack2D {
        fun translate(x: Float, y: Float) {
            last().translate(x, y, 0f)
        }

        fun scale(x: Float, y: Float) {
            last().scale(x, y, 1f)
        }

        fun mulPose(by: Quaternionfc) {
            last().rotate(by)
        }

        fun pushPose() {
            pose.pushPose()
        }

        fun popPose() {
            pose.popPose()
        }

        fun last(): PoseStack.Pose {
            return pose.last()
        }
    }

    class AlphaStack {
        private val stack = ArrayDeque<Float>()

        init {
            stack.push(1.0f)
        }

        fun push(alpha: Float) {
            stack.push(peek() * alpha)
        }

        fun pop() {
            if (stack.size > 1) stack.pop()
        }

        fun peek(): Float {
            val value = stack.peek()
            return value ?: 1.0f
        }

        fun clear() {
            stack.clear()
            stack.push(1.0f)
        }
    }

    class DrawOrderStack {
        private val stack = ArrayDeque<Int>()

        init {
            stack.push(0)
        }

        fun push(x: Int = peek()) {
            stack.push(x)
        }

        fun pop() {
            if (stack.size > 1) stack.pop()
        }

        fun advance(x: Int = 1) {
            stack.push(stack.pop() + x)
        }

        fun peek(): Int {
            val value = stack.peek()
            return value ?: 0
        }

        fun clear() {
            stack.clear()
            stack.push(0)
        }
    }

    class ScissorStack {
        private val stack = ArrayDeque<ScissorRect>()

        fun push(scissor: ScissorRect) {
            val currentScissor = stack.peekLast()
            if (currentScissor != null) {
                val intersection = scissor.intersection(currentScissor)
                stack.addLast(
                    Objects.requireNonNullElseGet<ScissorRect>(
                        intersection,
                        Supplier { ScissorRect.empty() })
                )
            } else stack.addLast(scissor)
        }

        fun pop() {
            check(!stack.isEmpty()) { "Scissor stack underflow" }
            stack.removeLast()
        }

        fun peek(): ScissorRect? {
            return stack.peekLast()
        }
    }
}
