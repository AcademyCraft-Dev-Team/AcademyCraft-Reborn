package org.academy.api.client.gui.command

import com.mojang.blaze3d.vertex.PoseStack
import org.academy.api.client.gui.render.ScissorRect
import java.util.*

class SubmittedCommand(
    val command: DrawCommand,
    val pose: PoseStack.Pose,
    val scissorRect: ScissorRect?,
    val drawOrder: Int
) {
    val resourceKey: Long = calculateResourceKey(command)

    companion object {
        private fun calculateResourceKey(command: DrawCommand): Long {
            return Objects.hash(command.textures, command.uniforms).toLong()
        }
    }
}