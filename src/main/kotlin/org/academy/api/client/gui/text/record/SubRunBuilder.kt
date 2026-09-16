package org.academy.api.client.gui.text.record

import com.mojang.blaze3d.textures.GpuTextureView
import org.academy.api.client.gui.text.atlas.AtlasSpec
import org.academy.api.client.gui.text.device.GlyphExpander
import org.academy.api.client.gui.text.model.GlyphKind
import org.academy.api.client.gui.text.model.TextBlob

object SubRunBuilder {
    fun make(
        blob: TextBlob,
        fontSize: Float, thickness: Float,
        red: Float, green: Float, blue: Float, alpha: Float,
        contentScale: Float, deviceScale: Float, guiScale: Float,
        revealCodeUnits: Int = Int.MAX_VALUE,
        fadeViewportLeft: Float = 0f, fadeViewportWidth: Float = 0f,
        fadeLength: Float = 0f, fadeLeftStrength: Float = 0f, fadeRightStrength: Float = 0f
    ): MutableList<GlyphRunDrawCommand> {
        val deviceGlyphs = GlyphExpander.layout(
            blob, fontSize, contentScale, deviceScale, guiScale, revealCodeUnits,
            fadeViewportLeft, fadeViewportWidth, fadeLength, fadeLeftStrength, fadeRightStrength
        )

        val groups = LinkedHashMap<RunKey, MutableList<GlyphQuad>>()
        for (g in deviceGlyphs) {
            if (!g.x.isFinite() || !g.y.isFinite() || !g.width.isFinite() || !g.height.isFinite()) continue
            val key = RunKey(g.kind, g.textureView)
            groups.getOrPut(key) { ArrayList() }.add(
                GlyphQuad(
                    g.x, g.y, g.width, g.height, g.u0, g.v0, g.u1, g.v1,
                    red, green, blue, alpha, g.fadeLeft, g.fadeRight
                )
            )
        }

        val commands: MutableList<GlyphRunDrawCommand> = ArrayList(groups.size)
        for ((key, quads) in groups) {
            commands.add(
                GlyphBatchDrawCommand(
                    key.kind,
                    key.texture,
                    quads,
                    AtlasSpec.DEFAULT_PX_RANGE,
                    thickness
                )
            )
        }
        return commands
    }

    private data class RunKey(val kind: GlyphKind, val texture: GpuTextureView)
}
