package org.academy.api.client.gui.render

import com.mojang.blaze3d.vertex.PoseStack
import org.academy.api.client.gui.command.DrawCommand
import org.academy.api.client.gui.command.SubmittedCommand
import java.util.*
import java.util.function.Supplier

class RenderContext {
    val commands: MutableList<SubmittedCommand> = ArrayList<SubmittedCommand>()
    private val pose: PoseStack = PoseStack()
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

    fun pose(): PoseStack {
        return pose
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

        fun advance() {
            stack.push(stack.pop() + 1)
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

        fun containsPoint(x: Int, y: Int): Boolean {
            if (stack.isEmpty()) return true
            return stack.peekLast().containsPoint(x, y)
        }
    }
}