package org.academy.api.client.gui.render

import com.mojang.blaze3d.vertex.PoseStack
import org.academy.api.client.gui.command.DrawCommand
import org.academy.api.client.gui.command.SubmittedCommand
import org.academy.api.client.gui.environment.UiEnvironment
import org.joml.Matrix4f
import org.joml.Quaternionfc
import java.util.*
import kotlin.math.max
import kotlin.math.sqrt

class Canvas {
    val commands = mutableListOf<SubmittedCommand>()

    /**
     * 单位换算来源。与 CTM（[pose]）一起构成文本的设备变换：文本在展开期用
     * `maxScale(CTM) × density` 决定位图/MSDF 与亚像素相位，而不是回读命令快照。
     */
    val density get() = UiEnvironment.get().density

    private val pose = PoseStack()
    private val pose2D = PoseStack2D()
    private val scissorStack: ScissorStack
    private val alphaStack: AlphaStack
    var commandCounter = 0
        private set

    val blurRegions = mutableListOf<BlurRegion>()

    init {
        scissorStack = ScissorStack()
        alphaStack = AlphaStack()
    }

    fun submit(command: DrawCommand) {
        val currentPose = pose.last().copy()
        val currentScissor = scissorStack.peek()
        commands.add(SubmittedCommand(command, currentPose, currentScissor, commandCounter))
        commandCounter++
    }

    fun addCached(cache: SubtreeCache, currentOrigin: Matrix4f): Boolean {
        // 空裁剪下缓存内容整体不可见, 直接视为已处理 (也避免登记被裁掉的模糊区域).
        if (isClippedOut()) return true

        val cachedCommands = cache.commands
        if (cachedCommands.isEmpty() && cache.regions.isEmpty()) return true

        val anchor =
            if (cachedCommands.isNotEmpty()) cachedCommands.first().commandIndex else cache.regions.first().commandIndex
        val offset = commandCounter - anchor

        val alphaMul = alphaMulCorrection(cache)
        val poseChanged = !SubtreeCache.sameMatrix(cache.recordOrigin, currentOrigin)

        if (poseChanged) {
            if (cache.regions.isNotEmpty()) return false
            addRecomposed(cachedCommands, offset, currentOrigin, cache.recordOrigin, alphaMul)
            return true
        }

        if (cachedCommands.isNotEmpty()) {
            for (c in cachedCommands) {
                commands.add(
                    SubmittedCommand(
                        c.command,
                        c.pose,
                        replayScissor(c),
                        c.commandIndex + offset,
                        alphaMul
                    )
                )
            }
            commandCounter = commands.size
        }
        for ((x, y, width, height, radius, commandIndex) in cache.regions) {
            blurRegions.add(BlurRegion(x, y, width, height, radius, commandIndex + offset))
        }
        return true
    }

    private fun addRecomposed(
        cachedCommands: List<SubmittedCommand>,
        offset: Int,
        currentOrigin: Matrix4f,
        recordOrigin: Matrix4f,
        alphaMul: Float
    ) {
        if (cachedCommands.isEmpty()) return
        val invRecordOrigin = Matrix4f(recordOrigin).invert() ?: return
        for (c in cachedCommands) {
            val replayPose = SubtreeCache.recomposePose(c.pose, currentOrigin, invRecordOrigin)
            commands.add(
                SubmittedCommand(
                    c.command,
                    replayPose,
                    replayScissor(c),
                    c.commandIndex + offset,
                    alphaMul
                )
            )
        }
        commandCounter = commands.size
    }

    private fun replayScissor(c: SubmittedCommand): ScissorRect? = c.scissorRect ?: scissorStack.peek()

    private fun alphaMulCorrection(cache: SubtreeCache): Float {
        val record = cache.recordAlphaMul
        val current = alphaStack.peek()
        if (record == current) return 1.0f
        if (record == 0f) return 1.0f
        return current / record
    }

    fun pose(): PoseStack2D {
        return pose2D
    }

    fun alpha(): AlphaStack {
        return alphaStack
    }

    fun registerBlurRegion(region: BlurRegion) {
        // 空裁剪 (控件完全移出可视区) 下不登记模糊区域, 对标 Skia 的 quickReject.
        if (isClippedOut()) return
        blurRegions.add(region)
    }

    fun blurRegionCount(): Int = blurRegions.size

    fun blurRegionsSince(fromIndex: Int): List<BlurRegion> =
        if (fromIndex < blurRegions.size) blurRegions.subList(fromIndex, blurRegions.size).toList()
        else emptyList()

    fun enableScissor(scissorRect: ScissorRect) {
        scissorStack.push(scissorRect)
    }

    fun disableScissor() {
        scissorStack.pop()
    }

    /**
     * 在当前 CTM 下裁剪本地坐标矩形, 对标 Android `Canvas.clipRect`:
     * 传入的是控件本地坐标, 会随 [pose] (含 scale/rotation/pivot) 变换到 gui 空间后再与祖先裁剪求交.
     * 与之相对, [enableScissor(ScissorRect)] 接收的是已解析的 gui 空间绝对矩形.
     */
    fun clipRect(left: Float, top: Float, right: Float, bottom: Float) {
        scissorStack.push(ScissorRect.fromLocal(left, top, right, bottom, pose.last().pose()))
    }

    fun currentScissor(): ScissorRect? = scissorStack.peek()

    /** 当前 clip 是否为空 (存在裁剪且无可见区域). */
    fun isClippedOut(): Boolean = scissorStack.peek()?.isEmpty == true

    val accumulatedAlpha: Float
        get() = alphaStack.peek()

    fun clear() {
        commands.clear()
        alphaStack.clear()
        blurRegions.clear()
        commandCounter = 0
    }

    companion object {
        /**
         * 从 CTM 读取缩放幅度的辅助（对标 Skia 的 `SkStrikeSpec` device matrix）。
         *
         * 只关心 2D 仿射的轴缩放：取两列长度最大值，旋转/镜像（负缩放）不影响结果。
         * 非均匀缩放时取较大轴，避免该轴欠采样。
         */
        fun maxScale(matrix: Matrix4f): Float {
            val axisX = sqrt(matrix.m00() * matrix.m00() + matrix.m10() * matrix.m10())
            val axisY = sqrt(matrix.m01() * matrix.m01() + matrix.m11() * matrix.m11())
            return max(axisX, axisY)
        }
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

    class ScissorStack {
        private val stack = ArrayDeque<ScissorRect>()

        fun push(scissor: ScissorRect) {
            val currentScissor = stack.peekLast()
            val result = when {
                currentScissor == null -> scissor
                // 对标 Skia 的冗余裁剪消除: 新裁剪完全包含当前裁剪时, 求交结果不变.
                scissor.encompasses(currentScissor) -> currentScissor
                else -> scissor.intersection(currentScissor) ?: ScissorRect.empty()
            }
            stack.addLast(result)
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
