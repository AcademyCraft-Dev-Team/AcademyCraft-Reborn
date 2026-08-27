package org.academy.api.client.gui.render

import com.mojang.blaze3d.vertex.PoseStack
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.command.SubmittedCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 验证 UI_LAYER 模糊的命令切分: 以模糊区域的 commandIndex 为界,
 * 将命令列表切分为多个段, 支持嵌套模糊面板喵.
 */
class UiContextSplitTest {

    private fun command(commandIndex: Int): SubmittedCommand {
        val pose = PoseStack().last()
        return SubmittedCommand(
            FillRectDrawCommand(1f, 1f, 1f, 1f, 1f, 1f),
            pose,
            null,
            drawOrder = 0L,
            commandIndex = commandIndex
        )
    }

    @Test
    fun `commands below the first blur index go to the first segment`() {
        val commands = listOf(command(0), command(1), command(2), command(3), command(4))
        val regions = listOf(BlurRegion(0f, 0f, 50f, 50f, 8f, commandIndex = 3))

        val segments = UiContext.splitCommandsForBlurByIndex(commands, regions)

        assertEquals(3, segments.size)
        assertEquals(listOf(0, 1, 2), segments[0].first.map { it.commandIndex })
        assertEquals(1, segments[1].second.size)
        assertEquals(listOf(3, 4), segments[2].first.map { it.commandIndex })
    }

    @Test
    fun `blur at the very beginning leaves no commands before it`() {
        val commands = listOf(command(0), command(1), command(2))
        val regions = listOf(BlurRegion(0f, 0f, 50f, 50f, 8f, commandIndex = 0))

        val segments = UiContext.splitCommandsForBlurByIndex(commands, regions)

        assertTrue(segments[0].first.isEmpty())
        assertEquals(listOf(0, 1, 2), segments.drop(1).flatMap { it.first }.map { it.commandIndex })
    }

    @Test
    fun `blur at the very end puts all commands before it`() {
        val commands = listOf(command(0), command(1), command(2))
        val regions = listOf(BlurRegion(0f, 0f, 50f, 50f, 8f, commandIndex = 3))

        val segments = UiContext.splitCommandsForBlurByIndex(commands, regions)

        assertEquals(2, segments.size)
        assertEquals(listOf(0, 1, 2), segments[0].first.map { it.commandIndex })
        assertTrue(segments[1].first.isEmpty())
    }

    @Test
    fun `multiple blur regions at different indices produce correct segments`() {
        val commands = listOf(command(0), command(1), command(2), command(3), command(4))
        val regions = listOf(
            BlurRegion(0f, 0f, 10f, 10f, 8f, commandIndex = 2),
            BlurRegion(0f, 0f, 20f, 20f, 12f, commandIndex = 4)
        )

        val segments = UiContext.splitCommandsForBlurByIndex(commands, regions)

        assertEquals(5, segments.size)
        assertEquals(listOf(0, 1), segments[0].first.map { it.commandIndex })
        assertEquals(1, segments[1].second.size)
        assertEquals(listOf(2, 3), segments[2].first.map { it.commandIndex })
        assertEquals(1, segments[3].second.size)
        assertEquals(listOf(4), segments[4].first.map { it.commandIndex })
    }

    @Test
    fun `blur regions at the same index are grouped into one segment`() {
        val commands = listOf(command(0), command(1), command(2))
        val regions = listOf(
            BlurRegion(0f, 0f, 10f, 10f, 8f, commandIndex = 1),
            BlurRegion(20f, 20f, 30f, 30f, 12f, commandIndex = 1)
        )

        val segments = UiContext.splitCommandsForBlurByIndex(commands, regions)

        assertEquals(3, segments.size)
        assertEquals(listOf(0), segments[0].first.map { it.commandIndex })
        assertEquals(2, segments[1].second.size)
        assertEquals(listOf(1, 2), segments[2].first.map { it.commandIndex })
    }
}
