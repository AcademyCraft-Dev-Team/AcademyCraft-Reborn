package org.academy.api.client.gui.render

import com.mojang.blaze3d.vertex.PoseStack
import org.academy.api.client.gui.command.DrawCommand
import org.academy.api.client.gui.command.SubmittedCommand
import org.joml.Quaternionfc
import java.util.*
import java.util.function.Supplier

class RenderContext {
    val commands: MutableList<SubmittedCommand> = ArrayList<SubmittedCommand>()
    private val pose = PoseStack()
    private val pose2D = PoseStack2D()
    private val scissorStack: ScissorStack
    private val drawOrderStack: DrawOrderStack
    private val alphaStack: AlphaStack

    init {
        scissorStack = ScissorStack()
        drawOrderStack = DrawOrderStack()
        alphaStack = AlphaStack()
    }

    fun submit(command: DrawCommand) {
        val currentPose = pose.last().copy()
        val currentScissor = scissorStack.peek()
        val currentDrawOrder = drawOrderStack.peek()
        commands.add(SubmittedCommand(command, currentPose, currentScissor, currentDrawOrder))
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
        private val stack: Deque<Float> = ArrayDeque<Float>()

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
        private val stack: Deque<Int> = ArrayDeque<Int>()

        init {
            stack.push(0)
        }

        fun push() {
            stack.push(peek())
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
        private val stack: Deque<ScissorRect> = ArrayDeque<ScissorRect>()

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