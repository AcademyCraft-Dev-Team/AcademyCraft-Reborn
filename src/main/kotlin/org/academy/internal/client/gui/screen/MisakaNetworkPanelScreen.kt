package org.academy.internal.client.gui.screen

import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.drawable.TextureDrawable
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.gui.widget.*
import org.academy.api.client.resources.R
import org.academy.internal.common.network.misaka.MisakaNetManageDataPacket
import org.academy.internal.common.network.misaka.MisakaPanelDataPacket
import org.academy.internal.common.network.misaka.RequestMisakaNetManagePacket
import org.academy.internal.common.network.misaka.SetMisakaNetworkAllocationPacket
import org.academy.internal.common.network.misaka.SetMisakaNetworkNodePacket
import org.academy.internal.common.network.misaka.SetMisakaWanderStylePacket
import org.academy.internal.common.world.entity.misaka.MisakaPersonality
import org.academy.internal.common.world.entity.misaka.MobRelation
import org.academy.internal.common.world.entity.misaka.WanderStyle
import org.academy.internal.server.misaka.MisakaComputeSink
import org.academy.internal.server.misaka.WirelessForwardingMisakaNAT
import org.misaka.MisakaNetworkClient
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class MisakaNetworkPanelScreen(
    private val data: MisakaPanelDataPacket
) : UiScreen(Component.translatable("screen.academy.misaka_network_panel")) {

    private lateinit var mainPage: LinearLayoutWidget
    private lateinit var bindPage: LinearLayoutWidget
    private lateinit var managePage: LinearLayoutWidget
    private lateinit var sistersTabContent: FrameLayoutWidget
    private lateinit var allocTabContent: FrameLayoutWidget
    private lateinit var sistersList: ListWidget<MisakaNetManageDataPacket.SisterSummary>
    private lateinit var pageLabel: LabelWidget
    private lateinit var allocatedLabel: LabelWidget
    private lateinit var sistersTabButton: ButtonWidget
    private lateinit var allocTabButton: ButtonWidget
    private lateinit var emptySistersLabel: LabelWidget
    private lateinit var networkTotalMskLabel: LabelWidget

    private var manageTab = ManageTab.SISTERS
    private var managePageIndex = 0
    private var manageTotalCount = 0
    private var manageTotalMsk = 0f
    private var localPercents = IntArray(MisakaComputeSink.COUNT)
    private val allocSeekBars = arrayOfNulls<SeekBarWidget>(MisakaComputeSink.COUNT)
    private val allocInputs = arrayOfNulls<TextBoxWidget>(MisakaComputeSink.COUNT)
    private var suppressAllocCallbacks = false

    override fun onInit() {
        val panel = FrameLayoutWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .gravity(Gravity.CENTER)
                .size(PANEL_WIDTH, PANEL_HEIGHT)
        }
        root.addChild("panel", panel)

        panel.addChild("background", FillWidget(ROOT_PLANE).apply {
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

        mainPage = buildMainPage()
        bindPage = buildBindPage()
        managePage = buildManagePage()
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
        refreshAllocWidgets()
        if (::allocatedLabel.isInitialized) {
            allocatedLabel.text = Component.translatable(
                "screen.academy.misaka_net_allocated",
                MisakaComputeSink.sum(localPercents)
            ).string
        }
    }

    private fun buildMainPage(): LinearLayoutWidget {
        val page = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = SPACING_MINOR
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        }

        page.addChild("title", LabelWidget(title.string).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
                .gravity(Gravity.CENTER)
        })
        page.addChild("title_rule", FillWidget(PRIMARY_FOREGROUND).apply {
            alpha = 0.8f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(1f)
                .marginTop(1f)
                .marginBottom(1f)
        })

        page.addChild(
            "serial",
            infoRow(
                Component.translatable("screen.academy.misaka_serial_label").string,
                Component.translatable("screen.academy.misaka_serial_value", data.serial()).string
            )
        )
        if (data.reconstructionWork()) {
            page.addChild(
                "badge",
                statusLine(
                    Component.translatable("screen.academy.misaka_reconstruction_badge").string,
                    ACCENT_CAPACITY
                )
            )
        }
        page.addChild(
            "perception",
            infoRow(
                Component.translatable("screen.academy.misaka_perception_label").string,
                data.perception().toString()
            )
        )
        page.addChild(
            "msk",
            infoRow(
                Component.translatable("screen.academy.misaka_msk_label").string,
                Component.translatable("screen.academy.misaka_msk_value", "%.1f".format(data.msk())).string
            )
        )
        page.addChild(
            "personality",
            infoRow(
                Component.translatable("screen.academy.misaka_personality_label").string,
                personalityName()
            )
        )
        page.addChild(
            "node",
            infoRow(
                Component.translatable("screen.academy.misaka_node_label").string,
                data.currentNodeName().ifEmpty {
                    Component.translatable("screen.academy.misaka_node_none").string
                }
            )
        )
        page.addChild(
            "relation",
            infoRow(
                Component.translatable("screen.academy.misaka_relation_label").string,
                relationName()
            )
        )
        if (data.reconstructionBlocked()) {
            page.addChild(
                "reconstruction",
                statusLine(
                    Component.translatable("screen.academy.misaka_reconstruction_blocked").string,
                    ACCENT_WARNING
                )
            )
        }

        if (data.privilege()) {
            page.addChild("section_rule_wander", sectionRule())
            page.addChild(
                "wander_label",
                sectionLabel(Component.translatable("screen.academy.misaka_wander_style").string)
            )
            val wanderRow = LinearLayoutWidget().apply {
                orientation = Orientation.HORIZONTAL
                spacing = SPACING_MINOR
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(LIST_ITEM_HEIGHT)
            }
            page.addChild("wander_row", wanderRow)
            wanderRow.addChild("waiting", styleButton(WanderStyle.WAITING))
            wanderRow.addChild("free", styleButton(WanderStyle.FREE_MOVE))
            wanderRow.addChild("follow", styleButton(WanderStyle.FOLLOW))
        }

        if (data.reconstructionWork() && data.privilege() && data.currentNodeName().isNotEmpty()) {
            page.addChild("section_rule_manage", sectionRule())
            page.addChild("manage_entry", manageMenuEntry())
        }

        val canBind = data.relation() >= MobRelation.DEFAULT.ordinal
        if (canBind && data.availableNodes().isNotEmpty()) {
            page.addChild("section_rule_nodes", sectionRule())
            page.addChild("bind_entry", bindMenuEntry())
        }

        page.addChild("section_rule_close", sectionRule())
        page.addChild("close", textActionButton(
            Component.translatable("screen.academy.misaka_close").string
        ) { onClose() })
        return page
    }

    private fun buildBindPage(): LinearLayoutWidget {
        val page = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = BIND_SPACING_MINOR
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            visibility = Widget.Visibility.GONE
            isEnabled = false
        }

        page.addChild("top_bar", createSubmenuTopBar(
            Component.translatable("screen.academy.misaka_bind_node").string
        ))
        page.addChild("top_rule", FillWidget(PRIMARY_FOREGROUND).apply {
            alpha = 0.8f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(1f)
                .marginBottom(1f)
        })

        page.addChild("icon", ImageWidget(R.textures.gui.icon.icon_tonode).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .size(16f, 16f)
        })
        page.addChild(
            "connected_label",
            LabelWidget(Component.translatable("screen.academy.misaka_bind_connected").string)
        )

        val connectedNode = data.currentNodeName()
        val connectedIsNone = connectedNode.isEmpty()
        val connectedContainer = FrameLayoutWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(LIST_ITEM_HEIGHT)
                .marginTop(BIND_SPACING_MINOR - BIND_SPACING_MAJOR)
                .marginRight(SCROLLBAR_WIDTH + BIND_SPACING_MINOR)
        }
        page.addChild("connected_node_container", connectedContainer)
        connectedContainer.addChild(
            "connected_node",
            wirelessStyleNodeRow(
                if (connectedIsNone) {
                    Component.translatable("screen.academy.misaka_bind_none").string
                } else {
                    connectedNode
                },
                isConnected = true,
                isNone = connectedIsNone
            ).apply {
                layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            }
        )

        page.addChild(
            "available_label",
            LabelWidget(Component.translatable("screen.academy.misaka_bind_available").string)
        )

        val listContainer = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = BIND_SPACING_MINOR
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .height(0f)
                .marginTop(BIND_SPACING_MINOR - BIND_SPACING_MAJOR)
        }
        page.addChild("list_container", listContainer)

        val scrollPanel = ScrollPanelWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .width(0f)
                .heightMode(SizeMode.MATCH_PARENT)
        }
        listContainer.addChild("scroll_panel", scrollPanel)
        listContainer.addChild("scroll_bar", ScrollBarWidget(scrollPanel, Orientation.VERTICAL).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(SCROLLBAR_WIDTH)
                .heightMode(SizeMode.MATCH_PARENT)
        })

        val nodeList = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
        }
        scrollPanel.setContent(nodeList)

        for (nodeName in data.availableNodes()) {
            if (nodeName == connectedNode) {
                continue
            }
            nodeList.addChild(
                "node_$nodeName",
                wirelessStyleNodeRow(nodeName, isConnected = false, isNone = false).apply {
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                        .height(LIST_ITEM_HEIGHT)
                }
            )
        }
        return page
    }

    private fun buildManagePage(): LinearLayoutWidget {
        val page = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = SPACING_MINOR
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            visibility = Widget.Visibility.GONE
            isEnabled = false
        }

        page.addChild("top_bar", createSubmenuTopBar(
            Component.translatable("screen.academy.misaka_net_manage").string
        ))
        page.addChild("top_rule", FillWidget(PRIMARY_FOREGROUND).apply {
            alpha = 0.8f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(1f)
                .marginBottom(1f)
        })

        val tabs = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = SPACING_MINOR
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(LIST_ITEM_HEIGHT)
        }
        page.addChild("tabs", tabs)
        sistersTabButton = tabButton(
            Component.translatable("screen.academy.misaka_net_tab_sisters").string,
            ManageTab.SISTERS
        )
        allocTabButton = tabButton(
            Component.translatable("screen.academy.misaka_net_tab_alloc").string,
            ManageTab.ALLOC
        )
        tabs.addChild("sisters", sistersTabButton)
        tabs.addChild("alloc", allocTabButton)

        val tabHost = FrameLayoutWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .height(0f)
        }
        page.addChild("tab_host", tabHost)

        sistersTabContent = buildSistersTab()
        allocTabContent = buildAllocTab()
        tabHost.addChild("sisters_tab", sistersTabContent)
        tabHost.addChild("alloc_tab", allocTabContent)
        showManageTab(ManageTab.SISTERS)
        return page
    }

    private fun buildSistersTab(): FrameLayoutWidget {
        val root = FrameLayoutWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        }
        val column = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = SPACING_MINOR
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        }
        root.addChild("column", column)

        networkTotalMskLabel = LabelWidget(
            Component.translatable(
                "screen.academy.misaka_net_total_msk",
                String.format(Locale.ROOT, "%.1f", manageTotalMsk)
            ).string
        ).apply {
            scale = 0.75f
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
        }
        column.addChild("total_msk", networkTotalMskLabel)
        column.addChild("header", sisterColumnsRow(
            Component.translatable("screen.academy.misaka_net_col_serial").string,
            Component.translatable("screen.academy.misaka_net_col_perception").string,
            Component.translatable("screen.academy.misaka_net_col_msk").string,
            Component.translatable("screen.academy.misaka_net_col_node").string,
            Component.translatable("screen.academy.misaka_net_col_coverage").string,
            Component.translatable("screen.academy.misaka_net_col_status").string,
            header = true
        ))
        column.addChild("header_rule", FillWidget(PRIMARY_FOREGROUND).apply {
            alpha = 0.35f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(1f)
        })

        val listHost = FrameLayoutWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .height(0f)
        }
        column.addChild("list_host", listHost)

        val scrollPanel = ScrollPanelWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .marginRight(SCROLLBAR_WIDTH + SPACING_MINOR)
        }
        listHost.addChild("scroll_panel", scrollPanel)
        listHost.addChild("scroll_bar", ScrollBarWidget(scrollPanel, Orientation.VERTICAL).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .width(SCROLLBAR_WIDTH)
                .heightMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER_RIGHT)
        })
        emptySistersLabel = LabelWidget(
            Component.translatable("screen.academy.misaka_net_empty").string
        ).apply {
            scale = 0.75f
            alpha = 0.7f
            visibility = Widget.Visibility.GONE
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER)
        }
        listHost.addChild("empty", emptySistersLabel)

        sistersList = ListWidget<MisakaNetManageDataPacket.SisterSummary>().apply {
            itemHeight = { _, _ -> LIST_ITEM_HEIGHT }
            spacing = SPACING_MICRO
            createItem = { _ -> FrameLayoutWidget() }
            bindItem = { view, item, _ ->
                view.clearChildren()
                view.addChild("back", FillWidget(LIST_ROW_FILL).apply {
                    alpha = 0.2f
                    layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
                })
                val status = if (item.starving()) {
                    Component.translatable("screen.academy.misaka_net_starving").string
                } else {
                    Component.translatable("screen.academy.misaka_net_status_ok").string
                }
                val coverage = if (item.inCoverage()) {
                    Component.translatable("screen.academy.misaka_net_coverage_in").string
                } else {
                    Component.translatable("screen.academy.misaka_net_coverage_out").string
                }
                view.addChild(
                    "cols",
                    sisterColumnsRow(
                        Component.translatable("screen.academy.misaka_serial_value", item.serial()).string,
                        item.perception().toString(),
                        String.format(Locale.ROOT, "%.1f", item.msk()),
                        item.nodeName().ifEmpty { "-" },
                        coverage,
                        status,
                        header = false,
                        coverageAccent = !item.inCoverage(),
                        statusAccent = item.starving()
                    ).apply {
                        layoutParams = FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT)
                            .paddingHorizontal(2f)
                            .gravity(Gravity.CENTER_VERTICAL)
                    }
                )
            }
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
        }
        scrollPanel.setContent(sistersList)

        val pager = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = SPACING_MINOR
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(LIST_ITEM_HEIGHT)
        }
        column.addChild("pager", pager)
        pager.addChild("prev", textActionButton(
            Component.translatable("screen.academy.misaka_net_prev").string
        ) {
            if (managePageIndex > 0) {
                requestManagePage(managePageIndex - 1)
            }
        }.apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .heightMode(SizeMode.MATCH_PARENT)
        })
        pageLabel = LabelWidget("1/1").apply {
            scale = 0.75f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER)
        }
        pager.addChild("page", pageLabel)
        pager.addChild("next", textActionButton(
            Component.translatable("screen.academy.misaka_net_next").string
        ) {
            val maxPage = if (manageTotalCount <= 0) 0 else (manageTotalCount - 1) / WirelessForwardingMisakaNAT.MANAGE_PAGE_SIZE
            if (managePageIndex < maxPage) {
                requestManagePage(managePageIndex + 1)
            }
        }.apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .heightMode(SizeMode.MATCH_PARENT)
        })
        return root
    }

    private fun sisterColumnsRow(
        serial: String,
        perception: String,
        msk: String,
        node: String,
        coverage: String,
        status: String,
        header: Boolean,
        coverageAccent: Boolean = false,
        statusAccent: Boolean = false
    ): LinearLayoutWidget {
        val row = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = SPACING_MINOR
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(if (header) 10f else LIST_ITEM_HEIGHT)
        }
        fun cell(text: String, width: Float, accent: Boolean = false): LabelWidget = LabelWidget(text).apply {
            scale = 0.7f
            alpha = if (header) 0.62f else if (accent) 1f else 0.9f
            if (accent) {
                setRed(((ACCENT_WARNING shr 16) and 0xFF) / 255f)
                setGreen(((ACCENT_WARNING shr 8) and 0xFF) / 255f)
                setBlue((ACCENT_WARNING and 0xFF) / 255f)
            }
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(width)
                .height(10f)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        row.addChild("serial", cell(serial, COL_SERIAL))
        row.addChild("perception", cell(perception, COL_PERCEPTION))
        row.addChild("msk", cell(msk, COL_MSK))
        row.addChild("node", LabelWidget(node).apply {
            scale = 0.7f
            alpha = if (header) 0.62f else 0.9f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER_VERTICAL)
        })
        row.addChild("coverage", cell(coverage, COL_COVERAGE, coverageAccent))
        row.addChild("status", cell(status, COL_STATUS, statusAccent))
        return row
    }

    private fun buildAllocTab(): FrameLayoutWidget {
        val root = FrameLayoutWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            visibility = Widget.Visibility.GONE
            isEnabled = false
        }
        val column = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = SPACING_MINOR
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        }
        root.addChild("column", column)

        val listHost = FrameLayoutWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .height(0f)
        }
        column.addChild("list_host", listHost)
        val scrollPanel = ScrollPanelWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .marginRight(SCROLLBAR_WIDTH + SPACING_MINOR)
        }
        listHost.addChild("scroll_panel", scrollPanel)
        listHost.addChild("scroll_bar", ScrollBarWidget(scrollPanel, Orientation.VERTICAL).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .width(SCROLLBAR_WIDTH)
                .heightMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER_RIGHT)
        })

        val rows = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = SPACING_MICRO
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
        }
        scrollPanel.setContent(rows)

        for (sink in MisakaComputeSink.entries) {
            rows.addChild("sink_${sink.id()}", allocRow(sink))
        }

        allocatedLabel = LabelWidget(
            Component.translatable("screen.academy.misaka_net_allocated", 0).string
        ).apply {
            scale = 0.75f
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
        }
        column.addChild("allocated", allocatedLabel)
        return root
    }

    private fun allocRow(sink: MisakaComputeSink): FrameLayoutWidget {
        val row = FrameLayoutWidget().apply {
            background = ColorDrawable(ROW_PLANE)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(ALLOC_ROW_HEIGHT)
        }
        val column = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = SPACING_MICRO
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .padding(4f, 3f)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        row.addChild("column", column)

        val heading = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = SPACING_MINOR
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(INPUT_HEIGHT)
        }
        column.addChild("heading", heading)
        heading.addChild("label", LabelWidget(
            Component.translatable("screen.academy.misaka_net_sink.${sink.serializedName()}").string
        ).apply {
            scale = 0.7f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(INPUT_HEIGHT)
                .gravity(Gravity.CENTER_VERTICAL)
        })

        val valueGroup = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = 1f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .height(INPUT_HEIGHT)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        heading.addChild("value", valueGroup)

        val input = TextBoxWidget(3).apply {
            setInputValidator { text -> text.isEmpty() || text.all { it.isDigit() } }
            layoutParams = LinearLayoutWidget.LayoutParams()
                .size(INPUT_WIDTH, INPUT_HEIGHT)
                .gravity(Gravity.CENTER)
        }
        allocInputs[sink.id()] = input
        valueGroup.addChild("input", input)
        valueGroup.addChild("pct", LabelWidget(
            Component.translatable("screen.academy.misaka_net_pct").string
        ).apply {
            scale = 0.7f
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .size(PCT_WIDTH, INPUT_HEIGHT)
                .gravity(Gravity.CENTER)
        })

        val seek = AllocSeekBar().apply {
            setMin(0f)
            setMax(100f)
            setProgress(0f)
            setKeyProgressIncrement(1)
            setBackgroundColor(SLIDER_TRACK)
            setProgressColor(PRIMARY_FOREGROUND)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(SLIDER_HEIGHT)
        }
        allocSeekBars[sink.id()] = seek
        column.addChild("seek", seek)

        seek.setOnSeekBarChangeListener(object : SeekBarWidget.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBarWidget, progress: Float, fromUser: Boolean) {
                if (suppressAllocCallbacks || !fromUser) {
                    return
                }
                setLocalPercent(sink.id(), progress.roundToInt())
                if (seekBar.progress.roundToInt() != localPercents[sink.id()]) {
                    suppressAllocCallbacks = true
                    seekBar.setProgress(localPercents[sink.id()].toFloat())
                    suppressAllocCallbacks = false
                }
                syncAllocInput(sink.id())
            }

            override fun onStartTrackingTouch(seekBar: SeekBarWidget) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBarWidget) {
                if (!suppressAllocCallbacks) {
                    commitAllocations()
                }
            }
        })
        input.setOnFocusLost {
            if (suppressAllocCallbacks) {
                return@setOnFocusLost
            }
            val parsed = input.text.toIntOrNull() ?: localPercents[sink.id()]
            setLocalPercent(sink.id(), parsed)
            syncAllocWidgetsFromLocal(sink.id())
            commitAllocations()
        }
        return row
    }

    private fun setLocalPercent(index: Int, requested: Int) {
        val others = MisakaComputeSink.sum(localPercents) - localPercents[index]
        localPercents[index] = max(0, minOf(100 - others, requested))
        if (::allocatedLabel.isInitialized) {
            allocatedLabel.text = Component.translatable(
                "screen.academy.misaka_net_allocated",
                MisakaComputeSink.sum(localPercents)
            ).string
        }
    }

    private fun syncAllocInput(index: Int) {
        suppressAllocCallbacks = true
        allocInputs[index]?.text = localPercents[index].toString()
        suppressAllocCallbacks = false
    }

    private fun syncAllocWidgetsFromLocal(index: Int) {
        suppressAllocCallbacks = true
        allocSeekBars[index]?.setProgress(localPercents[index].toFloat())
        allocInputs[index]?.text = localPercents[index].toString()
        suppressAllocCallbacks = false
    }

    private fun refreshAllocWidgets() {
        for (i in 0 until MisakaComputeSink.COUNT) {
            syncAllocWidgetsFromLocal(i)
        }
    }

    private fun commitAllocations() {
        localPercents = MisakaComputeSink.clampAllocations(localPercents)
        refreshAllocWidgets()
        MisakaNetworkClient.send(
            SetMisakaNetworkAllocationPacket(data.misakaUuid(), localPercents, managePageIndex)
        )
    }

    private fun requestManagePage(page: Int) {
        MisakaNetworkClient.send(RequestMisakaNetManagePacket(data.misakaUuid(), page))
    }

    private fun createSubmenuTopBar(titleText: String): LinearLayoutWidget {
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

    private fun manageMenuEntry(): ButtonWidget {
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

    private fun bindMenuEntry(): ButtonWidget {
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

    private fun tabButton(text: String, tab: ManageTab): ButtonWidget {
        val button = ButtonWidget().apply {
            background = actionBackground(manageTab == tab)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .heightMode(SizeMode.MATCH_PARENT)
            onClickListener = { showManageTab(tab) }
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

    private fun showMainPage() {
        mainPage.visibility = Widget.Visibility.VISIBLE
        mainPage.isEnabled = true
        bindPage.visibility = Widget.Visibility.GONE
        bindPage.isEnabled = false
        managePage.visibility = Widget.Visibility.GONE
        managePage.isEnabled = false
    }

    private fun showBindPage() {
        mainPage.visibility = Widget.Visibility.GONE
        mainPage.isEnabled = false
        bindPage.visibility = Widget.Visibility.VISIBLE
        bindPage.isEnabled = true
        managePage.visibility = Widget.Visibility.GONE
        managePage.isEnabled = false
    }

    private fun showManagePage() {
        mainPage.visibility = Widget.Visibility.GONE
        mainPage.isEnabled = false
        bindPage.visibility = Widget.Visibility.GONE
        bindPage.isEnabled = false
        managePage.visibility = Widget.Visibility.VISIBLE
        managePage.isEnabled = true
        showManageTab(ManageTab.SISTERS)
        requestManagePage(0)
    }

    private fun showManageTab(tab: ManageTab) {
        manageTab = tab
        sistersTabContent.visibility =
            if (tab == ManageTab.SISTERS) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
        sistersTabContent.isEnabled = tab == ManageTab.SISTERS
        allocTabContent.visibility =
            if (tab == ManageTab.ALLOC) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
        allocTabContent.isEnabled = tab == ManageTab.ALLOC
        if (::sistersTabButton.isInitialized) {
            sistersTabButton.background = actionBackground(tab == ManageTab.SISTERS)
            allocTabButton.background = actionBackground(tab == ManageTab.ALLOC)
        }
    }

    private fun wirelessStyleNodeRow(
        nodeName: String,
        isConnected: Boolean,
        isNone: Boolean
    ): FrameLayoutWidget {
        val nodeViewPanel = FrameLayoutWidget()
        nodeViewPanel.addChild("back", FillWidget(LIST_ROW_FILL).apply {
            alpha = 0.25f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .padding(2f, 2f)
        })

        val itemContent = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = 4f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER_VERTICAL)
                .paddingHorizontal(4f)
        }
        nodeViewPanel.addChild("content", itemContent)
        itemContent.addChild("icon", ImageWidget(R.textures.gui.icon.icon_node).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .gravity(Gravity.CENTER)
                .size(14f, 14f)
        })
        itemContent.addChild("node_name", LabelWidget(nodeName).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER_VERTICAL)
        })

        if (!isConnected) {
            val connectButton = ButtonWidget().apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .gravity(Gravity.CENTER)
                    .size(14f, 14f)
                onClickListener = {
                    MisakaNetworkClient.send(
                        SetMisakaNetworkNodePacket(data.entityUuid(), nodeName)
                    )
                }
            }
            itemContent.addChild("button", connectButton)
            connectButton.addChild("content", ImageWidget().apply {
                val defaultDrawable = TextureDrawable(R.textures.gui.icon.icon_unconnected).apply {
                    tintColor = 0xFFE6E6E6.toInt()
                }
                val hoveredDrawable = TextureDrawable(R.textures.gui.icon.icon_unconnected).apply {
                    tintColor = PRIMARY_FOREGROUND
                }
                background = StateListDrawable().apply {
                    setDefault(defaultDrawable)
                    addState(Widget.HOVERED, hoveredDrawable)
                }
                layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            })
        } else if (!isNone) {
            val disconnectButton = ButtonWidget().apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .gravity(Gravity.CENTER)
                    .size(14f, 14f)
                onClickListener = {
                    MisakaNetworkClient.send(
                        SetMisakaNetworkNodePacket(data.entityUuid(), "")
                    )
                }
            }
            itemContent.addChild("button", disconnectButton)
            disconnectButton.addChild("content", ImageWidget().apply {
                val defaultDrawable = TextureDrawable(R.textures.gui.icon.icon_connected).apply {
                    tintColor = 0xFFE6E6E6.toInt()
                }
                val hoveredDrawable = TextureDrawable(R.textures.gui.icon.icon_connected).apply {
                    tintColor = PRIMARY_FOREGROUND
                }
                background = StateListDrawable().apply {
                    setDefault(defaultDrawable)
                    addState(Widget.HOVERED, hoveredDrawable)
                }
                layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            })
        }
        return nodeViewPanel
    }

    private fun infoRow(label: String, value: String): FrameLayoutWidget {
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

    private fun statusLine(text: String, accent: Int): FrameLayoutWidget {
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

    private fun sectionLabel(text: String): LabelWidget = LabelWidget(text).apply {
        scale = 0.75f
        alpha = 0.7f
        layoutParams = LinearLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(8f)
    }

    private fun sectionRule(): FillWidget = FillWidget(PRIMARY_FOREGROUND).apply {
        alpha = 0.35f
        layoutParams = LinearLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(1f)
            .marginTop(2f)
            .marginBottom(1f)
    }

    private fun styleButton(style: WanderStyle): ButtonWidget {
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

    private fun textActionButton(text: String, onClick: () -> Unit): ButtonWidget {
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

    private fun actionBackground(selected: Boolean): StateListDrawable {
        val resting = ColorDrawable(if (selected) SELECTED_PLANE else ROW_PLANE)
        val hovered = ColorDrawable(HOVER_PLANE)
        return StateListDrawable().apply {
            setDefault(resting)
            addState(Widget.HOVERED, hovered)
            addState(Widget.FOCUSED, hovered)
            addState(Widget.PRESSED, ColorDrawable(SELECTED_PLANE))
        }
    }

    private fun personalityName(): String {
        val values = MisakaPersonality.entries
        val index = data.personality()
        if (index !in values.indices) {
            return Component.translatable("screen.academy.misaka_unknown").string
        }
        return Component.translatable("misaka.personality.${values[index].serializedName}").string
    }

    private fun relationName(): String {
        val values = MobRelation.entries
        val index = data.relation()
        if (index !in values.indices) {
            return Component.translatable("screen.academy.misaka_unknown").string
        }
        return Component.translatable("misaka.relation.${values[index].name.lowercase()}").string
    }

    private fun wanderStyleName(style: WanderStyle): String =
        Component.translatable("misaka.wander_style.${style.name.lowercase()}").string

    private enum class ManageTab {
        SISTERS,
        ALLOC
    }

    private class AllocSeekBar : SeekBarWidget() {
        override fun canFocus(): Boolean = true

        override fun renderInternal(context: RenderContext) {
            super.renderInternal(context)
            val range = max - min
            if (width <= 0f || height <= 0f || range <= 0f) {
                return
            }
            val ratio = Mth.clamp((progress - min) / range, 0f, 1f)
            val markerX = Mth.clamp(width * ratio - MARKER_WIDTH * 0.5f, 0f, width - MARKER_WIDTH)
            context.pose().pushPose()
            context.pose().translate(markerX, -2f)
            context.submit(
                FillRectDrawCommand(
                    MARKER_WIDTH,
                    height + 4f,
                    1f,
                    1f,
                    1f,
                    context.accumulatedAlpha
                )
            )
            context.pose().popPose()
        }
    }

    companion object {
        private const val PANEL_WIDTH = 280f
        private const val PANEL_HEIGHT = 248f
        private const val PANEL_INSET = 10f
        private const val SPACING_MICRO = 2f
        private const val SPACING_MINOR = 3f
        private const val BIND_SPACING_MAJOR = 8f
        private const val BIND_SPACING_MINOR = 4f
        private const val INFO_ROW_HEIGHT = 14f
        private const val LIST_ITEM_HEIGHT = 18f
        private const val ALLOC_ROW_HEIGHT = 34f
        private const val SLIDER_HEIGHT = 9f
        private const val MARKER_WIDTH = 5f
        private const val INPUT_WIDTH = 22f
        private const val INPUT_HEIGHT = 10f
        private const val PCT_WIDTH = 8f
        private const val COL_SERIAL = 36f
        private const val COL_PERCEPTION = 24f
        private const val COL_MSK = 36f
        private const val COL_COVERAGE = 40f
        private const val COL_STATUS = 28f
        private const val SCROLLBAR_WIDTH = 5f
        private const val ROOT_PLANE = 0x70000000
        private const val ROW_PLANE = 0x28000000
        private const val HOVER_PLANE = 0x40FFFFFF
        private const val SELECTED_PLANE = 0x50FFFFFF
        private const val LIST_ROW_FILL = 0xFFFFFFFF.toInt()
        private const val PRIMARY_FOREGROUND = 0xFFFFFFFF.toInt()
        private const val SLIDER_TRACK = 0x40FFFFFF
        private const val ACCENT_CAPACITY = 0xFFFF6C00.toInt()
        private const val ACCENT_WARNING = 0xFFFF6C00.toInt()
    }
}
