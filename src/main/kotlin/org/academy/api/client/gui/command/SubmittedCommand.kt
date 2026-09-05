package org.academy.api.client.gui.command

import com.mojang.blaze3d.vertex.PoseStack
import org.academy.api.client.gui.render.ScissorRect
import java.util.*

class SubmittedCommand(
    val command: DrawCommand,
    val pose: PoseStack.Pose,
    val scissorRect: ScissorRect?,
    val drawOrder: Long,
    val commandIndex: Int = 0,
    /**
     * 顶点生成期的 alpha 校正乘子 (对齐安卓 RenderNode 合成) 喵.
     * 颜色在录制时已烘焙"自身 × 录制期祖先 alpha"; 当缓存回放时祖先 alpha 变化,
     * 以 correction = 当前累积 alpha / 录制累积 alpha 重打标, 顶点生成期回填,
     * 从而 alpha 变化不再需要深失效重录整棵子树.
     */
    val alphaMul: Float = 1.0f
) {
    val resourceKey: Long = calculateResourceKey(command)

    companion object {
        private fun calculateResourceKey(command: DrawCommand): Long {
            return Objects.hash(command.textures, command.uniforms).toLong()
        }
    }
}
