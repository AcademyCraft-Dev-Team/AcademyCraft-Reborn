package org.academy.internal.client.gui.screen

import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.network.chat.Component
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.gui.util.WirelessPanelUtil
import org.academy.api.client.gui.widget.*
import org.academy.api.client.resources.R
import org.academy.internal.common.network.misaka.MisakaNetManageDataPacket
import org.academy.internal.common.network.misaka.MisakaPanelDataPacket
import org.academy.internal.common.network.misaka.SetMisakaNetworkNodePacket
import org.academy.internal.common.network.misaka.SetMisakaWanderStylePacket
import org.academy.internal.common.world.entity.misaka.WanderStyle
import org.academy.internal.server.misaka.MisakaComputeSink
import org.academy.internal.server.misaka.WirelessForwardingMisakaNAT
import org.misaka.MisakaNetworkClient
import java.util.Locale

class MisakaNetworkPanelScreen(
    override val data: MisakaPanelDataPacket
) : UiScreen(Component.translatable("screen.academy.misaka_network_panel")), MisakaPanelHost {

    private lateinit var mainPage: LinearLayoutWidget
    private lateinit var bindPage: LinearLayoutWidget
    private lateinit var managePage: LinearLayoutWidget
    override lateinit var sistersTabContent: FrameLayoutWidget
    override lateinit var allocTabContent: FrameLayoutWidget
    override lateinit var sistersList: ListWidget<MisakaNetManageDataPacket.SisterSummary>
    override lateinit var pageLabel: LabelWidget
    override lateinit var allocatedLabel: LabelWidget
    override lateinit var sistersTabButton: ButtonWidget
    override lateinit var allocTabButton: ButtonWidget
    override lateinit var emptySistersLabel: LabelWidget
    override lateinit var networkTotalMskLabel: LabelWidget

    override var manageTab = MisakaPanelManageTab.SISTERS
    override var managePageIndex = 0
    override var manageTotalCount = 0
    override var manageTotalMsk = 0f
    override var localPercents = IntArray(MisakaComputeSink.COUNT)
    override val allocSeekBars = arrayOfNulls<SeekBarWidget>(MisakaComputeSink.COUNT)
    override val allocInputs = arrayOfNulls<TextBoxWidget>(MisakaComputeSink.COUNT)
    override var suppressAllocCallbacks = false

    override val screenTitle: String
        get() = title.string

    override val allocatedLabelInitialized: Boolean
        get() = ::allocatedLabel.isInitialized

    override fun onInit() {
        val panel = FrameLayoutWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .gravity(Gravity.CENTER)
                .size(PANEL_WIDTH, PANEL_HEIGHT)
        }
        root.addChild("panel", panel)

        panel.addChild("background", BlendQuadWidget().apply {
            alpha = 0.5f
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        })
        panel.addChild("top_rule", FillWidget(PRIMARY_FOREGROUND).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .gravity(Gravity.TOP)
                .height(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .marginHorizontal(4f)
        })
        panel.addChild("bottom_rule", FillWidget(PRIMARY_FOREGROUND).apply {
            alpha = 0.7f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .gravity(Gravity.BOTTOM)
                .height(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .marginHorizontal(4f)
        })

        val pageHost = FrameLayoutWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .padding(PANEL_INSET, 7f)
        }
        panel.addChild("page_host", pageHost)

        mainPage = MisakaPanelMainPage.build(this)
        bindPage = MisakaPanelBindPage.build(this)
        managePage = MisakaPanelManageUi.buildManagePage(this)
        pageHost.addChild("main_page", mainPage)
        pageHost.addChild("bind_page", bindPage)
        pageHost.addChild("manage_page", managePage)
        showMainPage()
    }

    fun applyManageData(packet: MisakaNetManageDataPacket) {
        if (packet.misakaUuid() != data.misakaUuid()) {
            return
        }
        managePageIndex = packet.pageIndex()
        manageTotalCount = packet.totalCount()
        manageTotalMsk = packet.totalMskPerSecond()
        localPercents = packet.percents()
        if (::sistersList.isInitialized) {
            sistersList.items = packet.sisters()
        }
        if (::networkTotalMskLabel.isInitialized) {
            networkTotalMskLabel.text = Component.translatable(
                "screen.academy.misaka_net_total_msk",
                String.format(Locale.ROOT, "%.1f", manageTotalMsk)
            ).string
        }
        if (::emptySistersLabel.isInitialized) {
            val empty = manageTotalCount <= 0
            emptySistersLabel.visibility =
                if (empty) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
            sistersList.visibility =
                if (empty) Widget.Visibility.GONE else Widget.Visibility.VISIBLE
        }
        if (::pageLabel.isInitialized) {
            val pages = if (manageTotalCount <= 0) 1 else (manageTotalCount + WirelessForwardingMisakaNAT.MANAGE_PAGE_SIZE - 1) /
                WirelessForwardingMisakaNAT.MANAGE_PAGE_SIZE
            pageLabel.text = Component.translatable(
                "screen.academy.misaka_net_page",
                managePageIndex + 1,
                pages
            ).string
        }
        MisakaPanelManageUi.refreshAllocWidgets(this)
        if (::allocatedLabel.isInitialized) {
            allocatedLabel.text = Component.translatable(
                "screen.academy.misaka_net_allocated",
                MisakaComputeSink.sum(localPercents)
            ).string
        }
    }

    override fun createSubmenuTopBar(titleText: String): LinearLayoutWidget {
        val bar = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = SPACING_MINOR
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(LIST_ITEM_HEIGHT)
        }
        val back = ButtonWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .size(16f, 16f)
                .gravity(Gravity.CENTER_VERTICAL)
            onClickListener = { showMainPage() }
        }
        back.addChild("arrow", ImageWidget(R.textures.gui.icon.arrow_back).apply {
            setSampler(FilterMode.LINEAR, false)
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        })
        bar.addChild("back", back)
        bar.addChild("title", LabelWidget(titleText).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER)
        })
        return bar
    }

    override fun manageMenuEntry(): ButtonWidget {
        val entry = ButtonWidget().apply {
            background = actionBackground(false)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(LIST_ITEM_HEIGHT)
            onClickListener = { showManagePage() }
        }
        val content = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = SPACING_MINOR
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .paddingHorizontal(7f)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        entry.addChild("content", content)
        content.addChild("icon", ImageWidget(R.textures.gui.icon.icon_node).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .size(14f, 14f)
                .gravity(Gravity.CENTER)
        })
        content.addChild("label", LabelWidget(
            Component.translatable("screen.academy.misaka_net_manage").string
        ).apply {
            scale = 0.75f
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER_VERTICAL)
        })
        content.addChild("chevron", LabelWidget(">").apply {
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .height(10f)
                .gravity(Gravity.CENTER_VERTICAL)
        })
        return entry
    }

    override fun bindMenuEntry(): ButtonWidget {
        val entry = ButtonWidget().apply {
            background = actionBackground(false)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(LIST_ITEM_HEIGHT)
            onClickListener = { showBindPage() }
        }
        val content = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = SPACING_MINOR
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .paddingHorizontal(7f)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        entry.addChild("content", content)
        content.addChild("icon", ImageWidget(R.textures.gui.icon.icon_tonode).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .size(14f, 14f)
                .gravity(Gravity.CENTER)
        })
        content.addChild("label", LabelWidget(
            Component.translatable("screen.academy.misaka_bind_node").string
        ).apply {
            scale = 0.75f
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER_VERTICAL)
        })
        content.addChild("count", LabelWidget(
            Component.translatable(
                "screen.academy.misaka_bind_node_count",
                data.availableNodes().size
            ).string
        ).apply {
            scale = 0.75f
            alpha = 0.7f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .height(10f)
                .gravity(Gravity.CENTER_VERTICAL)
        })
        content.addChild("chevron", LabelWidget(">").apply {
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .height(10f)
                .gravity(Gravity.CENTER_VERTICAL)
        })
        return entry
    }

    override fun showMainPage() {
        mainPage.visibility = Widget.Visibility.VISIBLE
        mainPage.isEnabled = true
        bindPage.visibility = Widget.Visibility.GONE
        bindPage.isEnabled = false
        managePage.visibility = Widget.Visibility.GONE
        managePage.isEnabled = false
    }

    override fun showBindPage() {
        mainPage.visibility = Widget.Visibility.GONE
        mainPage.isEnabled = false
        bindPage.visibility = Widget.Visibility.VISIBLE
        bindPage.isEnabled = true
        managePage.visibility = Widget.Visibility.GONE
        managePage.isEnabled = false
    }

    override fun showManagePage() {
        mainPage.visibility = Widget.Visibility.GONE
        mainPage.isEnabled = false
        bindPage.visibility = Widget.Visibility.GONE
        bindPage.isEnabled = false
        managePage.visibility = Widget.Visibility.VISIBLE
        managePage.isEnabled = true
        showManageTab(MisakaPanelManageTab.SISTERS)
        MisakaPanelManageUi.requestManagePage(this, 0)
    }

    override fun showManageTab(tab: MisakaPanelManageTab) {
        manageTab = tab
        sistersTabContent.visibility =
            if (tab == MisakaPanelManageTab.SISTERS) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
        sistersTabContent.isEnabled = tab == MisakaPanelManageTab.SISTERS
        allocTabContent.visibility =
            if (tab == MisakaPanelManageTab.ALLOC) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
        allocTabContent.isEnabled = tab == MisakaPanelManageTab.ALLOC
        if (::sistersTabButton.isInitialized) {
            sistersTabButton.background = actionBackground(tab == MisakaPanelManageTab.SISTERS)
            allocTabButton.background = actionBackground(tab == MisakaPanelManageTab.ALLOC)
        }
    }

    override fun wirelessStyleNodeRow(
        nodeName: String,
        isConnected: Boolean,
        isNone: Boolean
    ): FrameLayoutWidget {
        return WirelessPanelUtil.nodeRow(
            nodeName = nodeName,
            isConnected = isConnected,
            isNone = isNone,
            onConnect = {
                MisakaNetworkClient.send(SetMisakaNetworkNodePacket(data.entityUuid(), nodeName))
            },
            onDisconnect = {
                MisakaNetworkClient.send(SetMisakaNetworkNodePacket(data.entityUuid(), ""))
            }
        )
    }

    override fun infoRow(label: String, value: String): FrameLayoutWidget {
        val row = FrameLayoutWidget().apply {
            background = ColorDrawable(ROW_PLANE)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(INFO_ROW_HEIGHT)
        }
        val content = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = SPACING_MINOR
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .paddingHorizontal(7f)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        row.addChild("content", content)
        content.addChild("label", LabelWidget(label).apply {
            scale = 0.75f
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER_LEFT)
        })
        content.addChild("value", LabelWidget(value).apply {
            scale = 0.75f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .height(10f)
                .gravity(Gravity.CENTER_RIGHT)
        })
        return row
    }

    override fun statusLine(text: String, accent: Int): FrameLayoutWidget {
        val row = FrameLayoutWidget().apply {
            background = ColorDrawable(ROW_PLANE)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(INFO_ROW_HEIGHT)
        }
        val content = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = SPACING_MINOR
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .paddingHorizontal(7f)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        row.addChild("content", content)
        content.addChild("marker", FillWidget(accent).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .size(6.5f, 6.5f)
                .gravity(Gravity.CENTER_VERTICAL)
        })
        content.addChild("label", LabelWidget(text).apply {
            scale = 0.75f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER_VERTICAL)
        })
        return row
    }

    override fun sectionLabel(text: String): LabelWidget = LabelWidget(text).apply {
        scale = 0.75f
        alpha = 0.7f
        layoutParams = LinearLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(8f)
    }

    override fun sectionRule(): FillWidget = FillWidget(PRIMARY_FOREGROUND).apply {
        alpha = 0.35f
        layoutParams = LinearLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(1f)
            .marginTop(2f)
            .marginBottom(1f)
    }

    override fun styleButton(style: WanderStyle): ButtonWidget {
        val button = ButtonWidget().apply {
            background = actionBackground(false)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .heightMode(SizeMode.MATCH_PARENT)
            onClickListener = {
                MisakaNetworkClient.send(
                    SetMisakaWanderStylePacket(data.entityUuid(), style.ordinal)
                )
            }
        }
        button.addChild("label", LabelWidget(wanderStyleName(style)).apply {
            scale = 0.75f
            alpha = 0.82f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER)
        })
        return button
    }

    override fun textActionButton(text: String, onClick: () -> Unit): ButtonWidget {
        val button = ButtonWidget().apply {
            background = actionBackground(false)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(LIST_ITEM_HEIGHT)
            onClickListener = { onClick() }
        }
        button.addChild("label", LabelWidget(text).apply {
            scale = 0.75f
            alpha = 0.82f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER)
        })
        return button
    }

    override fun actionBackground(selected: Boolean): StateListDrawable {
        val resting = ColorDrawable(if (selected) SELECTED_PLANE else ROW_PLANE)
        val hovered = ColorDrawable(HOVER_PLANE)
        return StateListDrawable().apply {
            setDefault(resting)
            addState(Widget.HOVERED, hovered)
            addState(Widget.FOCUSED, hovered)
            addState(Widget.PRESSED, ColorDrawable(SELECTED_PLANE))
        }
    }

    private fun wanderStyleName(style: WanderStyle): String =
        Component.translatable("misaka.wander_style.${style.name.lowercase()}").string

    companion object {
        internal const val PANEL_WIDTH = 280f
        internal const val PANEL_HEIGHT = 248f
        internal const val PANEL_INSET = 10f
        internal const val SPACING_MICRO = 2f
        internal const val SPACING_MINOR = 3f
        internal const val BIND_SPACING_MAJOR = 8f
        internal const val BIND_SPACING_MINOR = 4f
        internal const val INFO_ROW_HEIGHT = 14f
        internal const val LIST_ITEM_HEIGHT = 18f
        internal const val ALLOC_ROW_HEIGHT = 34f
        internal const val SLIDER_HEIGHT = 9f
        internal const val MARKER_WIDTH = 5f
        internal const val INPUT_WIDTH = 22f
        internal const val INPUT_HEIGHT = 10f
        internal const val PCT_WIDTH = 8f
        internal const val COL_SERIAL = 36f
        internal const val COL_PERCEPTION = 24f
        internal const val COL_MSK = 36f
        internal const val COL_COVERAGE = 40f
        internal const val COL_STATUS = 28f
        internal const val SCROLLBAR_WIDTH = 5f
        internal const val ROW_PLANE = 0x28000000
        internal const val HOVER_PLANE = 0x40FFFFFF
        internal const val SELECTED_PLANE = 0x50FFFFFF
        internal const val LIST_ROW_FILL = 0xFFFFFFFF.toInt()
        internal const val PRIMARY_FOREGROUND = 0xFFFFFFFF.toInt()
        internal const val SLIDER_TRACK = 0x40FFFFFF
        internal const val ACCENT_CAPACITY = 0xFFFF6C00.toInt()
        internal const val ACCENT_WARNING = 0xFFFF6C00.toInt()
    }
}
