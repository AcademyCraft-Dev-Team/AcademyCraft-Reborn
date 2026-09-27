package org.academy.internal.client.gui.layout

import org.academy.api.client.gui.dsl.*
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.FrameLayoutWidget

internal class ProgramEditorLayout(
    val root: FrameLayoutWidget,
    val panel: FrameLayoutWidget,
    val palette: FrameLayoutWidget,
    val canvas: FrameLayoutWidget,
    val inspector: FrameLayoutWidget
)

internal enum class ProgramEditorVariant(val paletteWidth: Float, val inspectorWidth: Float) {
    COMPACT(18f, 18f),
    MEDIUM(96f, 18f),
    WIDE(112f, 128f);

    companion object {
        fun of(compactLeft: Boolean, compactRight: Boolean): ProgramEditorVariant = when {
            compactLeft -> COMPACT
            compactRight -> MEDIUM
            else -> WIDE
        }
    }
}

internal fun buildProgramEditorLayout(variant: ProgramEditorVariant): ProgramEditorLayout {
    val root = FrameLayoutWidget()
    root.layoutParams = FrameLayoutWidget.LayoutParams()

    val panel = FrameLayoutWidget()
    root.addChild("panel", panel)
    panel.lp {
        sizeMode(SizeMode.MATCH_PARENT)
        margin(2f)
    }

    panel.fill(PANEL_BACKGROUND, "panel_background") { matchParent() }
    panel.fill(PANEL_EDGE, "border_top") {
        matchWidth()
        height(1f)
        gravity(Gravity.TOP)
    }
    panel.fill(PANEL_EDGE, "border_bottom") {
        matchWidth()
        height(1f)
        gravity(Gravity.BOTTOM)
    }
    panel.fill(PANEL_EDGE_DIM, "border_left") {
        width(1f)
        matchHeight()
        gravity(Gravity.LEFT)
    }
    panel.fill(PANEL_EDGE_DIM, "border_right") {
        width(1f)
        matchHeight()
        gravity(Gravity.RIGHT)
    }
    panel.fill(DIVIDER, "title_divider") {
        matchWidth()
        height(1f)
        margin(7f, 20f, 7f, 0f)
    }
    panel.fill(ACCENT, "title_accent") {
        size(2f, 8f)
        margin(7f, 6f, 0f, 0f)
    }

    val body = panel.row("body", spacing = 3f) {
        lp {
            sizeMode(SizeMode.MATCH_PARENT)
            marginTop(22f)
            marginBottom(18f)
        }

        frame("palette") {
            lp {
                width(variant.paletteWidth)
                matchHeight()
            }
            fill(SECTION_BACKGROUND, "palette_background") { matchParent() }
        }
        frame("canvas") {
            lp {
                width(0f)
                matchHeight()
                weight(1f)
            }
            fill(CANVAS_BACKGROUND, "canvas_background") { matchParent() }
        }
        frame("inspector") {
            lp {
                width(variant.inspectorWidth)
                matchHeight()
            }
            fill(SECTION_BACKGROUND, "inspector_background") { matchParent() }
        }
    }

    return ProgramEditorLayout(
        root = root,
        panel = panel,
        palette = body.children["palette"] as FrameLayoutWidget,
        canvas = body.children["canvas"] as FrameLayoutWidget,
        inspector = body.children["inspector"] as FrameLayoutWidget
    )
}

private const val PANEL_BACKGROUND = 0x28000000
private const val PANEL_EDGE = 0xD9FFFFFF.toInt()
private const val PANEL_EDGE_DIM = 0x55FFFFFF.toInt()
private const val DIVIDER = 0x80FFFFFF.toInt()
private const val ACCENT = 0xFFFF6C00.toInt()
private const val SECTION_BACKGROUND = 0x18000000
private const val CANVAS_BACKGROUND = 0x20000000
