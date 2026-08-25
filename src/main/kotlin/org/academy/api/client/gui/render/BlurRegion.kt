package org.academy.api.client.gui.render

/**
 * 屏幕空间模糊区域喵. 坐标基于 GUI 缩放后的逻辑像素.
 *
 * [drawOrder] 记录模糊面板渲染时的绘制顺序: [UiContext] 以此为界把命令列表
 * 切分为「模糊之下的内容 (如 main)」与「模糊之上的内容 (如 cover)」两个子 pass,
 * 中间由 [UiCompositor] 把下方内容模糊后烘入目标.
 */
data class BlurRegion(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val radius: Float,
    val drawOrder: Int = 0
)
