package org.academy.api.client.gui.text.subrun

import com.mojang.blaze3d.textures.GpuTextureView
import org.academy.api.client.gui.command.GlyphQuad
import org.academy.api.client.gui.command.SubRunDrawCommand
import org.academy.api.client.gui.command.TextDrawCommand
import org.academy.api.client.gui.glyph.Constants
import org.academy.api.client.gui.text.TextBlob
import org.academy.api.client.gui.text.TextShaper
import org.academy.api.client.gui.text.layout.TextProcessor

/**
 * 从设备无关的 [TextBlob] 构建 sub-run 命令（对标 Skia `SubRunContainer::MakeInAlloc`）。
 *
 * 用 canvas CTM 得到的设备原点把 [TextProcessor] 展开的字形按 (bitmap/MSDF, 图集页) 分组，
 * 每组产出一条多实例 [SubRunDrawCommand]。
 */
object SubRunContainer {
    /**
     * Text-space convenience overload for record-time callers (labels, tooltips).
     * [deviceScale] = content × CTM 缩放；[originXGui]/[originYGui] 为 device 原点（Skia
     * deviceOrigin，用于亚像素相位）。
     */
    fun make(
        text: String,
        fontSize: Float, thickness: Float,
        red: Float, green: Float, blue: Float, alpha: Float,
        contentScale: Float = 1f,
        deviceScale: Float,
        guiScale: Float,
        originXGui: Float = 0f, originYGui: Float = 0f
    ): MutableList<TextDrawCommand> {
        return make(
            TextShaper.shape(text, fontSize),
            fontSize, thickness, red, green, blue, alpha,
            contentScale, deviceScale, guiScale, originXGui, originYGui
        )
    }

    /**
     * Expands [blob] with the canvas CTM, emitting ONE [SubRunDrawCommand] per
     * (kind, atlas page). [revealCodeUnits] truncates the visible glyphs.
     * The fade* parameters drive the AOSP horizontal fading edges (marquee).
     */
    fun make(
        blob: TextBlob,
        fontSize: Float, thickness: Float,
        red: Float, green: Float, blue: Float, alpha: Float,
        contentScale: Float, deviceScale: Float, guiScale: Float,
        originXGui: Float = 0f, originYGui: Float = 0f,
        revealCodeUnits: Int = Int.MAX_VALUE,
        fadeViewportLeft: Float = 0f, fadeViewportWidth: Float = 0f,
        fadeLength: Float = 0f, fadeLeftStrength: Float = 0f, fadeRightStrength: Float = 0f
    ): MutableList<TextDrawCommand> {
        val deviceGlyphs = TextProcessor.layout(
            blob, fontSize, contentScale, deviceScale, guiScale, originXGui, originYGui, revealCodeUnits,
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

        val commands: MutableList<TextDrawCommand> = ArrayList(groups.size)
        for ((key, quads) in groups) {
            commands.add(
                SubRunDrawCommand(
                    key.kind,
                    key.texture,
                    quads,
                    Constants.DEFAULT_PX_RANGE,
                    thickness
                )
            )
        }
        return commands
    }

    private data class RunKey(val kind: TextKind, val texture: GpuTextureView)
}
