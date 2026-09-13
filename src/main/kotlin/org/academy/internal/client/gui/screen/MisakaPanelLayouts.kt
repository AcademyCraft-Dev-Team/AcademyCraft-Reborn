package org.academy.internal.client.gui.screen

import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.LinearLayoutWidget
import org.academy.api.client.gui.widget.ScrollBarWidget
import org.academy.api.client.gui.widget.ScrollPanelWidget

/**
 * Shared “weight(1) scroll + bar” shell for Misaka panel pages so fixed-height
 * canvases never clip without a way to reach bottom controls.
 *
 * Important: put [ScrollPanelWidget] as MATCH_PARENT inside a [FrameLayoutWidget]
 * that owns the weight. A weight ScrollPanel (or nested weight Linear that fails
 * to resolve) can stay height=0 — content still paints because a zero scissor
 * fail-opens, but clicks never hit.
 */
internal object MisakaPanelLayouts {
    fun scrollBody(
        spacing: Float = MisakaNetworkPanelScreen.SPACING_MINOR,
        barWidth: Float = MisakaNetworkPanelScreen.SCROLLBAR_WIDTH,
        configureBody: LinearLayoutWidget.() -> Unit
    ): LinearLayoutWidget {
        val listHost = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            this.spacing = spacing
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .height(0f)
        }
        // FrameLayout owns the weight slot; scroll fills it. Matches LaunchPad / cabin ops.
        val scrollHost = FrameLayoutWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .width(0f)
                .heightMode(SizeMode.MATCH_PARENT)
        }
        val scrollPanel = ScrollPanelWidget(Orientation.VERTICAL).apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        }
        scrollHost.addChild("scroll_panel", scrollPanel)
        listHost.addChild("scroll_host", scrollHost)
        listHost.addChild(
            "scroll_bar",
            ScrollBarWidget(scrollPanel, Orientation.VERTICAL).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .width(barWidth)
                    .heightMode(SizeMode.MATCH_PARENT)
            }
        )
        val body = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            this.spacing = spacing
            layoutParams = FrameLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .heightMode(SizeMode.WRAP_CONTENT)
            configureBody()
        }
        scrollPanel.setContent(body)
        return listHost
    }

    /**
     * Tab root that fills the manage tab host. Sticky chrome stays outside the
     * scroll viewport; [configureScrollBody] is only the scrolling middle.
     */
    fun scrollTab(
        spacing: Float = MisakaNetworkPanelScreen.SPACING_MINOR,
        configureScrollBody: LinearLayoutWidget.() -> Unit
    ): FrameLayoutWidget {
        val root = FrameLayoutWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        }
        val column = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            this.spacing = spacing
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        }
        root.addChild("column", column)
        column.addChild("body", scrollBody(spacing, configureBody = configureScrollBody))
        return root
    }

    /**
     * Full-bleed tab column: caller adds sticky children and one [scrollBody] /
     * weight scroll host. Prefer this when selection/pager must stay clickable
     * and visible below a scrolling list.
     */
    fun tabColumn(
        spacing: Float = MisakaNetworkPanelScreen.SPACING_MINOR,
        configure: LinearLayoutWidget.() -> Unit
    ): FrameLayoutWidget {
        val root = FrameLayoutWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        }
        val column = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            this.spacing = spacing
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            configure()
        }
        root.addChild("column", column)
        return root
    }
}
