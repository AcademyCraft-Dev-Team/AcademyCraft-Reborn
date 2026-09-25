package org.academy.api.client.gui.render

/**
 * 屏幕空间模糊区域喵. 坐标基于 GUI 缩放后的逻辑像素.
 *
 * [commandIndex] 记录模糊面板在扁平命令列表中的渲染序号: [UiContext] 以此为界把命令列表
 * 切分为多个段, 每段独立渲染→模糊→合成, 支持嵌套模糊面板在不同树级的情况喵.
 */
data class BlurRegion(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val radius: Float,
    val commandIndex: Int = 0
)
