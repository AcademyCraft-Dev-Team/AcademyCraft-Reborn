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

    /** 命令列表的单调递增渲染序号, 供 BlurRegion 记录分割点喵. */
    var commandCounter = 0
        private set

    /** 本帧收集到的模糊区域快照。 */
    val blurRegions: List<BlurRegion>
        get() = blurRegionBuffer

    var recordedMax = 0L
        private set

    init {
        scissorStack = ScissorStack()
        drawOrderStack = DrawOrderStack()
        alphaStack = AlphaStack()
    }

    fun resetRecordedMax() {
        recordedMax = 0L
    }

    fun submit(command: DrawCommand) {
        val currentPose = pose.last().copy()
        val currentScissor = scissorStack.peek()
        val currentDrawOrder = drawOrderStack.peek()
        if (currentDrawOrder > recordedMax) recordedMax = currentDrawOrder
        commands.add(SubmittedCommand(command, currentPose, currentScissor, currentDrawOrder, commandCounter))
        commandCounter++
    }

    fun addCached(cached: List<SubmittedCommand>, regions: List<BlurRegion> = emptyList()) {
        if (cached.isEmpty() && regions.isEmpty()) return
        val anchor = if (cached.isNotEmpty()) cached.first().commandIndex else regions.first().commandIndex
        val offset = commandCounter - anchor
        for (command in cached) {
            if (command.drawOrder > recordedMax) recordedMax = command.drawOrder
        }
        if (cached.isNotEmpty()) {
            val start = commands.size
            commands.addAll(cached)
            if (offset != 0) {
                for (i in start until commands.size) {
                    val c = commands[i]
                    commands[i] = SubmittedCommand(c.command, c.pose, c.scissorRect, c.drawOrder, c.commandIndex + offset)
                }
            }
            commandCounter = commands.size
        }
        for (region in regions) {
            blurRegionBuffer.add(BlurRegion(region.x, region.y, region.width, region.height, region.radius, region.commandIndex + offset))
        }
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
        commandCounter = 0
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
        private val stack = ArrayDeque<Long>()

        init {
            stack.push(0L)
        }

        fun push(x: Long = peek()) {
            stack.push(x)
        }

        fun pop() {
            if (stack.size > 1) stack.pop()
        }

        fun advance(x: Long = 1L) {
            val next = stack.pop() + x
            // 防止 coverAllPrev 的 advance(recordedMax+1) 指数增长导致 Long 溢出回绕成负数.
            // 真实 GUI 的 drawOrder 远小于该上限, 不会触顶, 因此层级顺序不受影响.
            stack.push(if (next > MAX_ORDER) MAX_ORDER else next)
        }

        fun peek(): Long {
            val value = stack.peek()
            return value ?: 0L
        }

        fun clear() {
            stack.clear()
            stack.push(0L)
        }

        companion object {
            const val MAX_ORDER: Long = 1L shl 40
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
