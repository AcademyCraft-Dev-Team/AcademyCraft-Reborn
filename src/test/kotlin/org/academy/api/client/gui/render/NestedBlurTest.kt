package org.academy.api.client.gui.render

import com.mojang.blaze3d.vertex.PoseStack
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.command.SubmittedCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NestedBlurTest {
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
    fun `two blur regions at different indices produce correct segments`() {
        val commands = listOf(
            command(0), command(1), command(2),
            command(3), command(4), command(5),
            command(6), command(7)
        )
        val regions = listOf(
            BlurRegion(0f, 0f, 100f, 100f, 8f, commandIndex = 3),
            BlurRegion(20f, 20f, 50f, 50f, 12f, commandIndex = 6)
        )

        val segments = UiContext.splitCommandsForBlurByIndex(commands, regions)

        assertEquals(5, segments.size)
        assertEquals(listOf(0, 1, 2), segments[0].first.map { it.commandIndex })
        assertTrue(segments[0].second.isEmpty())
        assertEquals(1, segments[1].second.size)
        assertEquals(3, segments[1].second[0].commandIndex)
        assertEquals(listOf(3, 4, 5), segments[2].first.map { it.commandIndex })
        assertTrue(segments[2].second.isEmpty())
        assertEquals(1, segments[3].second.size)
        assertEquals(6, segments[3].second[0].commandIndex)
        assertEquals(listOf(6, 7), segments[4].first.map { it.commandIndex })
    }

    @Test
    fun `blur region at index 0 produces empty first segment`() {
        val commands = listOf(command(0), command(1), command(2))
        val regions = listOf(
            BlurRegion(0f, 0f, 100f, 100f, 8f, commandIndex = 0)
        )

        val segments = UiContext.splitCommandsForBlurByIndex(commands, regions)

        assertEquals(2, segments.size)
        assertTrue(segments[0].first.isEmpty())
        assertEquals(1, segments[0].second.size)
        assertEquals(listOf(0, 1, 2), segments[1].first.map { it.commandIndex })
    }

    @Test
    fun `blur region at the end produces empty last command segment`() {
        val commands = listOf(command(0), command(1), command(2))
        val regions = listOf(
            BlurRegion(0f, 0f, 100f, 100f, 8f, commandIndex = 3)
        )

        val segments = UiContext.splitCommandsForBlurByIndex(commands, regions)

        assertEquals(2, segments.size)
        assertEquals(listOf(0, 1, 2), segments[0].first.map { it.commandIndex })
        assertEquals(1, segments[1].second.size)
        assertTrue(segments[1].first.isEmpty())
    }

    @Test
    fun `three blur regions produce correct segments`() {
        val commands = listOf(
            command(0), command(1),
            command(2), command(3),
            command(4), command(5),
            command(6)
        )
        val regions = listOf(
            BlurRegion(0f, 0f, 10f, 10f, 8f, commandIndex = 2),
            BlurRegion(10f, 10f, 20f, 20f, 12f, commandIndex = 4),
            BlurRegion(30f, 30f, 40f, 40f, 16f, commandIndex = 6)
        )

        val segments = UiContext.splitCommandsForBlurByIndex(commands, regions)

        assertEquals(7, segments.size)
        assertEquals(listOf(0, 1), segments[0].first.map { it.commandIndex })
        assertEquals(1, segments[1].second.size)
        assertEquals(listOf(2, 3), segments[2].first.map { it.commandIndex })
        assertEquals(1, segments[3].second.size)
        assertEquals(listOf(4, 5), segments[4].first.map { it.commandIndex })
        assertEquals(1, segments[5].second.size)
        assertEquals(listOf(6), segments[6].first.map { it.commandIndex })
    }

    @Test
    fun `adjacent blur regions with no commands between them`() {
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
