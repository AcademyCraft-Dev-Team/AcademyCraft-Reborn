package org.academy.api.client.gui.render

import com.mojang.blaze3d.vertex.PoseStack
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.command.SubmittedCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 验证 UI_LAYER 模糊的命令切分: 以模糊区域最小 drawOrder 为界,
 * 下方内容 (main) 与上方内容 (cover) 两个子 pass 边界正确.
 */
class UiContextSplitTest {

    private fun command(drawOrder: Int): SubmittedCommand {
        val pose = PoseStack().last()
        return SubmittedCommand(
            FillRectDrawCommand(1f, 1f, 1f, 1f, 1f, 1f),
            pose,
            null,
            drawOrder
        )
    }

    @Test
    fun `commands below the min blur draw order go to the below pass`() {
        val commands = listOf(command(1), command(2), command(3), command(4), command(5))
        val regions = listOf(BlurRegion(0f, 0f, 50f, 50f, 8f, drawOrder = 3))

        val (below, above) = UiContext.splitCommandsForBlur(commands, regions)

        assertEquals(listOf(1, 2), below.map { it.drawOrder })
        assertEquals(listOf(3, 4, 5), above.map { it.drawOrder })
    }

    @Test
    fun `blur on top leaves no below pass`() {
        val commands = listOf(command(1), command(2), command(3))
        val regions = listOf(BlurRegion(0f, 0f, 50f, 50f, 8f, drawOrder = 1))

        val (below, above) = UiContext.splitCommandsForBlur(commands, regions)

        assertTrue(below.isEmpty())
        assertEquals(listOf(1, 2, 3), above.map { it.drawOrder })
    }

    @Test
    fun `blur at the very bottom puts everything above`() {
        val commands = listOf(command(1), command(2), command(3))
        val regions = listOf(BlurRegion(0f, 0f, 50f, 50f, 8f, drawOrder = 3))

        val (below, above) = UiContext.splitCommandsForBlur(commands, regions)

        assertEquals(listOf(1, 2), below.map { it.drawOrder })
        assertEquals(listOf(3), above.map { it.drawOrder })
    }

    @Test
    fun `lowest blur region wins when multiple panels present`() {
        val commands = listOf(command(1), command(2), command(3), command(4))
        val regions = listOf(
            BlurRegion(0f, 0f, 10f, 10f, 8f, drawOrder = 2),
            BlurRegion(0f, 0f, 20f, 20f, 12f, drawOrder = 4)
        )

        val (below, above) = UiContext.splitCommandsForBlur(commands, regions)

        assertEquals(listOf(1), below.map { it.drawOrder })
        assertEquals(listOf(2, 3, 4), above.map { it.drawOrder })
    }
}
