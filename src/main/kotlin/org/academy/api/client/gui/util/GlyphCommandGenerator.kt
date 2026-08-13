package org.academy.api.client.gui.util

import org.academy.api.client.gui.command.GlyphDrawCommand
import org.academy.api.client.gui.msdf.Constants
import org.academy.api.client.gui.msdf.layout.MsdfTextProcessor.layout

object GlyphCommandGenerator {
    fun generate(
        text: String,
        fontSize: Float, thickness: Float,
        red: Float, green: Float, blue: Float, alpha: Float
    ): MutableList<GlyphDrawCommand> {
        val commands: MutableList<GlyphDrawCommand> = mutableListOf()
        val result = layout(text, fontSize)

        for (instance in result.instances) {
            commands.add(
                GlyphDrawCommand(
                    instance.textureView,
                    instance.x,
                    instance.y,
                    instance.quadWidth,
                    instance.quadHeight,
                    instance.u0, instance.v0, instance.u1, instance.v1,
                    red, green, blue, alpha,
                    Constants.DEFAULT_PX_RANGE,
                    thickness
                )
            )
        }

        return commands
    }
}
