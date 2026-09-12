package org.academy.internal.client.gui.screen

import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.widget.*
import org.academy.internal.common.network.misaka.DisconnectMisakaFromNetworkPacket
import org.academy.internal.common.network.misaka.MisakaNetManageDataPacket
import org.academy.internal.common.network.misaka.RequestMisakaNetManagePacket
import org.academy.internal.common.network.misaka.SetMisakaNetworkAllocationPacket
import org.academy.internal.common.network.misaka.SetMisakaNetworkPermissionPacket
import org.academy.internal.server.misaka.MisakaComputeSink
import org.academy.internal.server.misaka.WirelessForwardingMisakaNAT
import org.misaka.MisakaNetworkClient
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

internal object MisakaPanelManageUi {
    private const val DISCONNECT_BUTTON_WIDTH = 44f

    private val EDITABLE_PERMISSIONS = listOf(
        "ACCESS",
        "CONFIGURE",
        "MEMBER",
        "ENTITY_CONFIG",
        "SATELLITE_USE",
        "SATELLITE_MANAGE",
        "DEVICE_MANAGE",
        "DESTROY",
        "ADMIN"
    )
    class AllocSeekBar : SeekBarWidget() {
        override fun canFocus(): Boolean = true

        override fun renderInternal(context: RenderContext) {
            super.renderInternal(context)
            val range = max - min
            if (width <= 0f || height <= 0f || range <= 0f) {
                return
            }
            val ratio = Mth.clamp((progress - min) / range, 0f, 1f)
            val markerX = Mth.clamp(width * ratio - MisakaNetworkPanelScreen.MARKER_WIDTH * 0.5f, 0f, width - MisakaNetworkPanelScreen.MARKER_WIDTH)
            context.pose().pushPose()
            context.pose().translate(markerX, -2f)
            context.submit(
                FillRectDrawCommand(
                    MisakaNetworkPanelScreen.MARKER_WIDTH,
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

    fun buildManagePage(host: MisakaPanelHost): LinearLayoutWidget {
        val page = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = MisakaNetworkPanelScreen.SPACING_MINOR
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            visibility = Widget.Visibility.GONE
            isEnabled = false
        }

        page.addChild("top_bar", host.createSubmenuTopBar(
            Component.translatable("screen.academy.misaka_net_manage").string
        ))
        page.addChild("top_rule", FillWidget(MisakaNetworkPanelScreen.PRIMARY_FOREGROUND).apply {
            alpha = 0.8f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(1f)
                .marginBottom(1f)
        })

        val tabs = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = MisakaNetworkPanelScreen.SPACING_MINOR
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(MisakaNetworkPanelScreen.LIST_ITEM_HEIGHT)
        }
        page.addChild("tabs", tabs)
        host.sistersTabButton = tabButton(host, 
            Component.translatable("screen.academy.misaka_net_tab_sisters").string,
            MisakaPanelManageTab.SISTERS
        )
        host.allocTabButton = tabButton(host, 
            Component.translatable("screen.academy.misaka_net_tab_alloc").string,
            MisakaPanelManageTab.ALLOC
        )
        host.membersTabButton = tabButton(
            host,
            Component.translatable("screen.academy.misaka_net_tab_members").string,
            MisakaPanelManageTab.MEMBERS
        )
        tabs.addChild("sisters", host.sistersTabButton)
        tabs.addChild("alloc", host.allocTabButton)
        tabs.addChild("members", host.membersTabButton)

        val tabHost = FrameLayoutWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .height(0f)
        }
        page.addChild("tab_host", tabHost)

        host.sistersTabContent = buildSistersTab(host)
        host.allocTabContent = buildAllocTab(host)
        host.membersTabContent = buildMembersTab(host)
        tabHost.addChild("sisters_tab", host.sistersTabContent)
        tabHost.addChild("alloc_tab", host.allocTabContent)
        tabHost.addChild("members_tab", host.membersTabContent)
        host.showManageTab(MisakaPanelManageTab.SISTERS)
        return page
    }

    fun buildMembersTab(host: MisakaPanelHost): FrameLayoutWidget {
        val root = FrameLayoutWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        }
        val column = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = MisakaNetworkPanelScreen.SPACING_MINOR
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        }
        root.addChild("column", column)
        column.addChild(
            "admins_title",
            host.sectionLabel(Component.translatable("screen.academy.misaka_net_admins_title").string)
        )
        host.membersListLabel = LabelWidget(formatMembers(host)).apply {
            scale = 0.72f
            alpha = 0.88f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .heightMode(SizeMode.WRAP_CONTENT)
        }
        column.addChild("members", host.membersListLabel)
        column.addChild("rule", host.sectionRule())
        column.addChild(
            "edit_title",
            host.sectionLabel(Component.translatable("screen.academy.misaka_net_perm_edit_title").string)
        )

        host.memberNameInput = TextBoxWidget(16).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(14f)
        }
        column.addChild("name_input", host.memberNameInput)

        val actions = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = MisakaNetworkPanelScreen.SPACING_MINOR
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(14f)
        }
        actions.addChild("cycle_perm", host.textActionButton(
            Component.translatable("screen.academy.misaka_net_perm_cycle").string
        ) {
            host.memberPermIndex =
                (host.memberPermIndex + 1) % EDITABLE_PERMISSIONS.size
            refreshMembersTab(host)
        })
        actions.addChild("grant", host.textActionButton(
            Component.translatable("screen.academy.misaka_net_perm_grant").string
        ) {
            sendPermissionEdit(host, grant = true)
        })
        actions.addChild("revoke", host.textActionButton(
            Component.translatable("screen.academy.misaka_net_perm_revoke").string
        ) {
            sendPermissionEdit(host, grant = false)
        })
        column.addChild("actions", actions)
        host.membersPermHintLabel = LabelWidget(formatSelectedPermission(host)).apply {
            scale = 0.7f
            alpha = 0.75f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
        }
        column.addChild("perm_hint", host.membersPermHintLabel)
        return root
    }

    fun refreshMembersTab(host: MisakaPanelHost) {
        if (host.membersListLabelInitialized) {
            host.membersListLabel.text = formatMembers(host)
        }
        if (host.membersPermHintLabelInitialized) {
            host.membersPermHintLabel.text = formatSelectedPermission(host)
        }
    }

    private fun formatMembers(host: MisakaPanelHost): String {
        val rows = host.manageMembers
        if (rows.isEmpty()) {
            return Component.translatable("screen.academy.misaka_net_admins_empty").string
        }
        return rows.joinToString("\n") { row ->
            val perms = if (row.permissions().isEmpty()) "-" else row.permissions().joinToString(",")
            val role = if (row.admin()) "ADMIN" else "MEMBER"
            "${row.name()} [$role] $perms"
        }
    }

    private fun formatSelectedPermission(host: MisakaPanelHost): String {
        val perm = EDITABLE_PERMISSIONS[host.memberPermIndex.coerceIn(0, EDITABLE_PERMISSIONS.lastIndex)]
        val lock = if (host.manageCanEditMembers) {
            ""
        } else {
            " — " + Component.translatable("screen.academy.misaka_net_perm_edit_locked").string
        }
        return Component.translatable("screen.academy.misaka_net_perm_selected", perm).string + lock
    }

    private fun sendPermissionEdit(host: MisakaPanelHost, grant: Boolean) {
        if (!host.manageCanEditMembers) {
            return
        }
        val name = host.memberNameInput.text.trim()
        if (name.isEmpty()) {
            return
        }
        val perm = EDITABLE_PERMISSIONS[host.memberPermIndex.coerceIn(0, EDITABLE_PERMISSIONS.lastIndex)]
        MisakaNetworkClient.send(
            SetMisakaNetworkPermissionPacket(
                host.data.misakaUuid(),
                name,
                perm,
                grant,
                host.managePageIndex
            )
        )
    }

    fun buildSistersTab(host: MisakaPanelHost): FrameLayoutWidget {
        val root = FrameLayoutWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        }
        val column = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = MisakaNetworkPanelScreen.SPACING_MINOR
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        }
        root.addChild("column", column)

        host.networkTotalMskLabel = LabelWidget(formatSupply(host)).apply {
            scale = 0.75f
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
        }
        column.addChild("total_msk", host.networkTotalMskLabel)
        host.networkDemandLabel = LabelWidget(formatDemand(host)).apply {
            scale = 0.75f
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
        }
        column.addChild("demand_msk", host.networkDemandLabel)
        host.networkSatisfactionLabel = LabelWidget(formatSatisfaction(host)).apply {
            scale = 0.75f
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
        }
        column.addChild("satisfaction", host.networkSatisfactionLabel)
        column.addChild("header", sisterColumnsRow(
            Component.translatable("screen.academy.misaka_net_col_serial").string,
            Component.translatable("screen.academy.misaka_net_col_perception").string,
            Component.translatable("screen.academy.misaka_net_col_msk").string,
            Component.translatable("screen.academy.misaka_net_col_node").string,
            Component.translatable("screen.academy.misaka_net_col_coverage").string,
            Component.translatable("screen.academy.misaka_net_col_status").string,
            header = true
        ))
        column.addChild("header_rule", FillWidget(MisakaNetworkPanelScreen.PRIMARY_FOREGROUND).apply {
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
                .marginRight(MisakaNetworkPanelScreen.SCROLLBAR_WIDTH + MisakaNetworkPanelScreen.SPACING_MINOR)
        }
        listHost.addChild("scroll_panel", scrollPanel)
        listHost.addChild("scroll_bar", ScrollBarWidget(scrollPanel, Orientation.VERTICAL).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .width(MisakaNetworkPanelScreen.SCROLLBAR_WIDTH)
                .heightMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER_RIGHT)
        })
        host.emptySistersLabel = LabelWidget(
            Component.translatable("screen.academy.misaka_net_empty").string
        ).apply {
            scale = 0.75f
            alpha = 0.7f
            visibility = Widget.Visibility.GONE
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER)
        }
        listHost.addChild("empty", host.emptySistersLabel)

        host.sistersList = ListWidget<MisakaNetManageDataPacket.SisterSummary>().apply {
            itemHeight = { _, _ -> MisakaNetworkPanelScreen.LIST_ITEM_HEIGHT }
            spacing = MisakaNetworkPanelScreen.SPACING_MICRO
            createItem = { _ -> FrameLayoutWidget() }
            bindItem = { view, item, _ ->
                view.clearChildren()
                val selected = item.misakaUuid() == host.selectedSisterUuid
                // The row itself is the selector; ejecting is then confirmed from the footer.
                view.addChild("back", ButtonWidget().apply {
                    background = sisterRowBackground(selected)
                    layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
                    onClickListener = {
                        host.selectedSisterUuid =
                            if (selected) null else item.misakaUuid()
                        refreshSisterSelection(host, rebuildRows = true)
                    }
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
        scrollPanel.setContent(host.sistersList)

        // §7.2 eject: pick a row above, read back who is targeted, then act.
        val selectionRow = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = MisakaNetworkPanelScreen.SPACING_MINOR
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(MisakaNetworkPanelScreen.INFO_ROW_HEIGHT)
        }
        val selectionLabel = LabelWidget(selectionText(host)).apply {
            scale = 0.7f
            alpha = 0.78f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        host.sisterSelectionLabel = selectionLabel
        selectionRow.addChild("selection", selectionLabel)
        val disconnectButton = host.textActionButton(
            Component.translatable("screen.academy.misaka_net_disconnect").string
        ) {
            sendDisconnect(host)
        }.apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(DISCONNECT_BUTTON_WIDTH)
                .height(MisakaNetworkPanelScreen.INFO_ROW_HEIGHT)
        }
        host.sisterDisconnectButton = disconnectButton
        selectionRow.addChild("disconnect", disconnectButton)
        column.addChild("selection_row", selectionRow)
        refreshSisterSelection(host)

        val pager = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = MisakaNetworkPanelScreen.SPACING_MINOR
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(MisakaNetworkPanelScreen.LIST_ITEM_HEIGHT)
        }
        column.addChild("pager", pager)
        pager.addChild("prev", host.textActionButton(
            Component.translatable("screen.academy.misaka_net_prev").string
        ) {
            if (host.managePageIndex > 0) {
                requestManagePage(host, host.managePageIndex - 1)
            }
        }.apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .heightMode(SizeMode.MATCH_PARENT)
        })
        host.pageLabel = LabelWidget("1/1").apply {
            scale = 0.75f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER)
        }
        pager.addChild("page", host.pageLabel)
        pager.addChild("next", host.textActionButton(
            Component.translatable("screen.academy.misaka_net_next").string
        ) {
            val maxPage = if (host.manageTotalCount <= 0) 0 else (host.manageTotalCount - 1) / WirelessForwardingMisakaNAT.MANAGE_PAGE_SIZE
            if (host.managePageIndex < maxPage) {
                requestManagePage(host, host.managePageIndex + 1)
            }
        }.apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .heightMode(SizeMode.MATCH_PARENT)
        })
        return root
    }

    /** Row plane ladder: keeps the list's white 20% resting fill, adds hover/selected states. */
    private fun sisterRowBackground(selected: Boolean): StateListDrawable {
        val resting = ColorDrawable(
            if (selected) {
                MisakaNetworkPanelScreen.SELECTED_PLANE
            } else {
                MisakaNetworkPanelScreen.ACTION_RESTING_PLANE
            }
        )
        return StateListDrawable().apply {
            setDefault(resting)
            addState(Widget.HOVERED, ColorDrawable(MisakaNetworkPanelScreen.HOVER_PLANE))
            addState(Widget.FOCUSED, ColorDrawable(MisakaNetworkPanelScreen.HOVER_PLANE))
            addState(Widget.PRESSED, ColorDrawable(MisakaNetworkPanelScreen.SELECTED_PLANE))
        }
    }

    private fun selectedSister(host: MisakaPanelHost): MisakaNetManageDataPacket.SisterSummary? {
        val selected = host.selectedSisterUuid ?: return null
        if (!host.sistersListInitialized) {
            return null
        }
        return host.sistersList.items.firstOrNull { it.misakaUuid() == selected }
    }

    private fun selectionText(host: MisakaPanelHost): String {
        val sister = selectedSister(host)
            ?: return Component.translatable("screen.academy.misaka_net_select_hint").string
        val serial = Component.translatable("screen.academy.misaka_serial_value", sister.serial()).string
        if (sister.misakaUuid() == host.data.misakaUuid()) {
            return Component.translatable("screen.academy.misaka_net_select_console", serial).string
        }
        return Component.translatable("screen.academy.misaka_net_selected", serial).string
    }

    /** A selection is ejectable unless it is the sister whose panel opened this console. */
    private fun canDisconnectSelection(host: MisakaPanelHost): Boolean {
        val sister = selectedSister(host) ?: return false
        return sister.misakaUuid() != host.data.misakaUuid()
    }

    /**
     * Sync the footer to the current selection, dropping it when the sister left the page.
     * [rebuildRows] is only needed when the selection changed without the data changing.
     */
    fun refreshSisterSelection(host: MisakaPanelHost, rebuildRows: Boolean = false) {
        if (host.selectedSisterUuid != null && selectedSister(host) == null) {
            host.selectedSisterUuid = null
        }
        host.sisterSelectionLabel?.text = selectionText(host)
        val enabled = canDisconnectSelection(host)
        host.sisterDisconnectButton?.let {
            it.isEnabled = enabled
            it.alpha = if (enabled) 1f else 0.4f
        }
        if (rebuildRows && host.sistersListInitialized) {
            // Virtualized rows rebuild from a fresh list instance, refreshing the highlight.
            host.sistersList.items = host.sistersList.items.toList()
        }
    }

    private fun sendDisconnect(host: MisakaPanelHost) {
        if (!canDisconnectSelection(host)) {
            return
        }
        val target = host.selectedSisterUuid ?: return
        host.selectedSisterUuid = null
        MisakaNetworkClient.send(
            DisconnectMisakaFromNetworkPacket(
                host.data.misakaUuid(),
                target,
                host.managePageIndex
            )
        )
    }

    fun sisterColumnsRow(
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
            spacing = MisakaNetworkPanelScreen.SPACING_MINOR
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(if (header) 10f else MisakaNetworkPanelScreen.LIST_ITEM_HEIGHT)
        }
        fun cell(text: String, width: Float, accent: Boolean = false): LabelWidget = LabelWidget(text).apply {
            scale = 0.7f
            alpha = if (header) 0.62f else if (accent) 1f else 0.9f
            if (accent) {
                setRed(((MisakaNetworkPanelScreen.ACCENT_WARNING shr 16) and 0xFF) / 255f)
                setGreen(((MisakaNetworkPanelScreen.ACCENT_WARNING shr 8) and 0xFF) / 255f)
                setBlue((MisakaNetworkPanelScreen.ACCENT_WARNING and 0xFF) / 255f)
            }
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(width)
                .height(10f)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        row.addChild("serial", cell(serial, MisakaNetworkPanelScreen.COL_SERIAL))
        row.addChild("perception", cell(perception, MisakaNetworkPanelScreen.COL_PERCEPTION))
        row.addChild("msk", cell(msk, MisakaNetworkPanelScreen.COL_MSK))
        row.addChild("node", LabelWidget(node).apply {
            scale = 0.7f
            alpha = if (header) 0.62f else 0.9f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER_VERTICAL)
        })
        row.addChild("coverage", cell(coverage, MisakaNetworkPanelScreen.COL_COVERAGE, coverageAccent))
        row.addChild("status", cell(status, MisakaNetworkPanelScreen.COL_STATUS, statusAccent))
        return row
    }

    fun buildAllocTab(host: MisakaPanelHost): FrameLayoutWidget {
        val root = FrameLayoutWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            visibility = Widget.Visibility.GONE
            isEnabled = false
        }
        val column = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = MisakaNetworkPanelScreen.SPACING_MINOR
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
                .marginRight(MisakaNetworkPanelScreen.SCROLLBAR_WIDTH + MisakaNetworkPanelScreen.SPACING_MINOR)
        }
        listHost.addChild("scroll_panel", scrollPanel)
        listHost.addChild("scroll_bar", ScrollBarWidget(scrollPanel, Orientation.VERTICAL).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .width(MisakaNetworkPanelScreen.SCROLLBAR_WIDTH)
                .heightMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER_RIGHT)
        })

        val rows = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = MisakaNetworkPanelScreen.SPACING_MICRO
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
        }
        scrollPanel.setContent(rows)

        for (sink in MisakaComputeSink.entries) {
            rows.addChild("sink_${sink.id()}", allocRow(host, sink))
        }

        host.allocatedLabel = LabelWidget(
            Component.translatable("screen.academy.misaka_net_allocated", 0).string
        ).apply {
            scale = 0.75f
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
        }
        column.addChild("allocated", host.allocatedLabel)
        return root
    }

    fun allocRow(host: MisakaPanelHost, sink: MisakaComputeSink): FrameLayoutWidget {
        val row = FrameLayoutWidget().apply {
            background = ColorDrawable(MisakaNetworkPanelScreen.ROW_PLANE)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(MisakaNetworkPanelScreen.ALLOC_ROW_HEIGHT)
        }
        val column = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = MisakaNetworkPanelScreen.SPACING_MICRO
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .padding(4f, 3f)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        row.addChild("column", column)

        val heading = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = MisakaNetworkPanelScreen.SPACING_MINOR
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(MisakaNetworkPanelScreen.INPUT_HEIGHT)
        }
        column.addChild("heading", heading)
        heading.addChild("label", LabelWidget(
            Component.translatable("screen.academy.misaka_net_sink.${sink.serializedName()}").string
        ).apply {
            scale = 0.7f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(MisakaNetworkPanelScreen.INPUT_HEIGHT)
                .gravity(Gravity.CENTER_VERTICAL)
        })

        val valueGroup = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = 1f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .height(MisakaNetworkPanelScreen.INPUT_HEIGHT)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        heading.addChild("value", valueGroup)

        val input = TextBoxWidget(3).apply {
            setInputValidator { text -> text.isEmpty() || text.all { it.isDigit() } }
            layoutParams = LinearLayoutWidget.LayoutParams()
                .size(MisakaNetworkPanelScreen.INPUT_WIDTH, MisakaNetworkPanelScreen.INPUT_HEIGHT)
                .gravity(Gravity.CENTER)
        }
        host.allocInputs[sink.id()] = input
        valueGroup.addChild("input", input)
        valueGroup.addChild("pct", LabelWidget(
            Component.translatable("screen.academy.misaka_net_pct").string
        ).apply {
            scale = 0.7f
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .size(MisakaNetworkPanelScreen.PCT_WIDTH, MisakaNetworkPanelScreen.INPUT_HEIGHT)
                .gravity(Gravity.CENTER)
        })

        val seek = AllocSeekBar().apply {
            setMin(0f)
            setMax(100f)
            setProgress(0f)
            setKeyProgressIncrement(1)
            setBackgroundColor(MisakaNetworkPanelScreen.SLIDER_TRACK)
            setProgressColor(MisakaNetworkPanelScreen.PRIMARY_FOREGROUND)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(MisakaNetworkPanelScreen.SLIDER_HEIGHT)
        }
        host.allocSeekBars[sink.id()] = seek
        column.addChild("seek", seek)

        seek.setOnSeekBarChangeListener(object : SeekBarWidget.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBarWidget, progress: Float, fromUser: Boolean) {
                if (host.suppressAllocCallbacks || !fromUser) {
                    return
                }
                setLocalPercent(host, sink.id(), progress.roundToInt())
                if (seekBar.progress.roundToInt() != host.localPercents[sink.id()]) {
                    host.suppressAllocCallbacks = true
                    seekBar.setProgress(host.localPercents[sink.id()].toFloat())
                    host.suppressAllocCallbacks = false
                }
                syncAllocInput(host, sink.id())
            }

            override fun onStartTrackingTouch(seekBar: SeekBarWidget) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBarWidget) {
                if (!host.suppressAllocCallbacks) {
                    commitAllocations(host)
                }
            }
        })
        input.setOnFocusLost {
            if (host.suppressAllocCallbacks) {
                return@setOnFocusLost
            }
            val parsed = input.text.toIntOrNull() ?: host.localPercents[sink.id()]
            setLocalPercent(host, sink.id(), parsed)
            syncAllocWidgetsFromLocal(host, sink.id())
            commitAllocations(host)
        }
        return row
    }

    fun setLocalPercent(host: MisakaPanelHost, index: Int, requested: Int) {
        val others = MisakaComputeSink.sum(host.localPercents) - host.localPercents[index]
        host.localPercents[index] = max(0, minOf(100 - others, requested))
        if (host.allocatedLabelInitialized) {
            host.allocatedLabel.text = Component.translatable(
                "screen.academy.misaka_net_allocated",
                MisakaComputeSink.sum(host.localPercents)
            ).string
        }
    }

    fun syncAllocInput(host: MisakaPanelHost, index: Int) {
        host.suppressAllocCallbacks = true
        host.allocInputs[index]?.text = host.localPercents[index].toString()
        host.suppressAllocCallbacks = false
    }

    fun syncAllocWidgetsFromLocal(host: MisakaPanelHost, index: Int) {
        host.suppressAllocCallbacks = true
        host.allocSeekBars[index]?.setProgress(host.localPercents[index].toFloat())
        host.allocInputs[index]?.text = host.localPercents[index].toString()
        host.suppressAllocCallbacks = false
    }

    fun refreshAllocWidgets(host: MisakaPanelHost) {
        for (i in 0 until MisakaComputeSink.COUNT) {
            syncAllocWidgetsFromLocal(host, i)
        }
    }

    fun commitAllocations(host: MisakaPanelHost) {
        host.localPercents = MisakaComputeSink.clampAllocations(host.localPercents)
        refreshAllocWidgets(host)
        MisakaNetworkClient.send(
            SetMisakaNetworkAllocationPacket(host.data.misakaUuid(), host.localPercents, host.managePageIndex)
        )
    }

    fun requestManagePage(host: MisakaPanelHost, page: Int) {
        MisakaNetworkClient.send(RequestMisakaNetManagePacket(host.data.misakaUuid(), page))
    }

    fun refreshNetworkMetrics(host: MisakaPanelHost) {
        if (host.networkTotalMskLabelInitialized) {
            host.networkTotalMskLabel.text = formatSupply(host)
        }
        if (host.networkDemandLabelInitialized) {
            host.networkDemandLabel.text = formatDemand(host)
        }
        if (host.networkSatisfactionLabelInitialized) {
            host.networkSatisfactionLabel.text = formatSatisfaction(host)
        }
    }

    private fun formatSupply(host: MisakaPanelHost): String {
        val idle = (host.manageTotalMsk - host.managePriorityAllocatedMsk - host.manageSharedAllocatedMsk)
            .coerceAtLeast(0f)
        return Component.translatable(
            "screen.academy.misaka_supply_pools_label",
            String.format(Locale.ROOT, "%.1f", host.manageTotalMsk),
            String.format(Locale.ROOT, "%.1f", host.managePriorityAllocatedMsk),
            String.format(Locale.ROOT, "%.1f", host.manageSharedAllocatedMsk),
            String.format(Locale.ROOT, "%.1f", idle)
        ).string
    }

    private fun formatDemand(host: MisakaPanelHost): String =
        Component.translatable(
            "screen.academy.misaka_demand_label",
            String.format(Locale.ROOT, "%.1f", host.manageDemandMsk)
        ).string

    private fun formatSatisfaction(host: MisakaPanelHost): String {
        val pct = (host.manageYourSatisfaction.coerceIn(0f, 1f) * 100f).roundToInt()
        return Component.translatable(
            "screen.academy.misaka_satisfaction_label",
            pct,
            String.format(Locale.ROOT, "%.1f", host.manageYourAllocatedMsk)
        ).string
    }

    fun tabButton(host: MisakaPanelHost, text: String, tab: MisakaPanelManageTab): ButtonWidget {
        val button = ButtonWidget().apply {
            background = host.actionBackground(host.manageTab == tab)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .heightMode(SizeMode.MATCH_PARENT)
            onClickListener = { host.showManageTab(tab) }
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
}
