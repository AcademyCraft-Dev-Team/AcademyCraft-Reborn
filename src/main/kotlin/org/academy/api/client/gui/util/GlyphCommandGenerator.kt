package org.academy.api.client.gui.util

import org.academy.api.client.gui.command.DrawCommand
import org.academy.api.client.gui.command.GlyphDrawCommand
import org.academy.api.client.gui.msdf.core.Constants
import org.academy.api.client.gui.msdf.layout.MsdfTextProcessor
import org.academy.api.client.gui.msdf.layout.MsdfTextProcessor.layout

object GlyphCommandGenerator {
    fun generate(
        text: String,
        fontSize: Float, thickness: Float,
        red: Float, green: Float, blue: Float, alpha: Float
    ): MutableList<DrawCommand> {
        val commands: MutableList<DrawCommand> = ArrayList()
        val instances: MutableList<MsdfTextProcessor.GlyphInstance> = layout(text, fontSize)

        for (instance in instances) {
            commands.add(
                GlyphDrawCommand(
                    instance.textureView,
                    instance.x,
                    instance.y,
                    instance.quadWidth,
                    instance.quadHeight,
                    instance.u0, instance.v0, instance.u1, instance.v1,
                    red, green, blue, alpha,
                    Constants.DEFAULT_PX_RANGE.toFloat(),
                    thickness
                )
            )
        }

        return commands
    }
}