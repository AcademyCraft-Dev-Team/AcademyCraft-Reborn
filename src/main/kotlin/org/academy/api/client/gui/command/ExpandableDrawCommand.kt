package org.academy.api.client.gui.command

import com.mojang.blaze3d.vertex.PoseStack

/**
 * 可在批处理期展开为若干子命令的命令（对标 Skia 的 `SubRunContainer` 展开）。
 *
 * 记录期只存与设备无关的数据；展开期在 [org.academy.api.client.gui.render.BatchProcessor]
 * 里拿到 canvas CTM（[pose]），据其决定位图/MSDF、亚像素相位并产出真正的绘制命令。
 */
interface ExpandableDrawCommand {
    fun expand(pose: PoseStack.Pose, alphaMul: Float): List<DrawCommand>
}
