package org.academy.api.client.gui.render

import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.DynamicUniformStorage.DynamicUniform
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.command.SubmittedCommand
import org.academy.api.client.render.UniformPayload
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BatchProcessorCullTest {
    private val uploader = object : BatchProcessor.UboUploader {
        override fun <T : DynamicUniform> upload(payload: UniformPayload<T>): GpuBufferSlice =
            error("culled commands must not be uploaded")
    }

    @Test
    fun `empty scissor commands are culled before batching`() {
        val pose = PoseStack().last()
        val command = FillRectDrawCommand(4f, 4f, 1f, 1f, 1f, 1f)
        val commands = mutableListOf(
            SubmittedCommand(command, pose, ScissorRect.empty(), 0),
            SubmittedCommand(command, pose, ScissorRect(0f, 0f, 0f, 16f), 1)
        )

        val batches = BatchProcessor.process(commands, uploader)

        assertTrue(batches.isEmpty())
    }
}
