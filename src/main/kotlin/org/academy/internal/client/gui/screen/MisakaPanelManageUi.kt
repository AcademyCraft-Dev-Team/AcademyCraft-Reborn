package org.academy.internal.client.gui.screen

import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.event.OnClickListener
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
        // Sticky access banner + column header + editor; only the member list scrolls.
        return MisakaPanelLayouts.tabColumn {
            host.membersAccessLabel = LabelWidget(formatMembersAccess(host)).apply {
                scale = 0.72f
                alpha = 0.78f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(10f)
            }
            addChild("access", host.membersAccessLabel)
            addChild(
                "list_title",
                host.sectionLabel(Component.translatable("screen.academy.misaka_net_members_list_title").string)
            )
            addChild("header", memberColumnsRow(
                Component.translatable("screen.academy.misaka_net_col_name").string,
                Component.translatable("screen.academy.misaka_net_col_role").string,
                Component.translatable("screen.academy.misaka_net_col_perms").string,
                header = true
            ))

            addChild(
                "list_scroll",
                MisakaPanelLayouts.scrollBody {
                    host.membersListLabel = LabelWidget(formatMembersEmptyHint(host)).apply {
                        scale = 0.72f
                        alpha = 0.7f
                        layoutParams = LinearLayoutWidget.LayoutParams()
                            .widthMode(SizeMode.MATCH_PARENT)
                            .heightMode(SizeMode.WRAP_CONTENT)
                    }
                    addChild("members_empty", host.membersListLabel)
                    host.membersListHost = LinearLayoutWidget().apply {
                        orientation = Orientation.VERTICAL
                        spacing = MisakaNetworkPanelScreen.SPACING_MICRO
                        isClickable = false
                        layoutParams = LinearLayoutWidget.LayoutParams()
                            .widthMode(SizeMode.MATCH_PARENT)
                            .heightMode(SizeMode.WRAP_CONTENT)
                    }
                    addChild("members_rows", host.membersListHost)
                }
            )

            addChild("editor_rule", host.sectionRule())
            host.membersEditorHost = LinearLayoutWidget().apply {
                orientation = Orientation.VERTICAL
                spacing = MisakaNetworkPanelScreen.SPACING_MICRO
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .heightMode(SizeMode.WRAP_CONTENT)
            }
            addChild("editor", host.membersEditorHost)

            host.membersEditorHost.addChild(
                "edit_title",
                host.sectionLabel(Component.translatable("screen.academy.misaka_net_perm_edit_title").string)
            )
            host.memberTargetLabel = LabelWidget(formatMemberTarget(host)).apply {
                scale = 0.7f
                alpha = 0.8f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(10f)
            }
            host.membersEditorHost.addChild("target", host.memberTargetLabel)

            host.memberNameInput = TextBoxWidget(16).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(14f)
            }
            host.membersEditorHost.addChild("name_input", host.memberNameInput)

            host.membersPermHintLabel = LabelWidget(formatSelectedPermission(host)).apply {
                scale = 0.68f
                alpha = 0.72f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(10f)
            }
            host.membersEditorHost.addChild("perm_hint", host.membersPermHintLabel)

            val actions = LinearLayoutWidget().apply {
                orientation = Orientation.HORIZONTAL
                spacing = MisakaNetworkPanelScreen.SPACING_MINOR
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(MisakaNetworkPanelScreen.LIST_ITEM_HEIGHT)
            }
            host.membersEditorHost.addChild("actions", actions)

            host.memberCycleButton = host.textActionButton(currentPermissionLabel(host)) {
                if (!host.manageCanEditMembers) {
                    return@textActionButton
                }
                host.memberPermIndex =
                    (host.memberPermIndex + 1) % EDITABLE_PERMISSIONS.size
                refreshMembersTab(host)
            }.apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1.4f)
                    .width(0f)
                    .heightMode(SizeMode.MATCH_PARENT)
            }
            host.memberGrantButton = host.textActionButton(
                Component.translatable("screen.academy.misaka_net_perm_grant").string
            ) {
                sendPermissionEdit(host, grant = true)
            }.apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .width(0f)
                    .heightMode(SizeMode.MATCH_PARENT)
            }
            host.memberRevokeButton = host.textActionButton(
                Component.translatable("screen.academy.misaka_net_perm_revoke").string
            ) {
                sendPermissionEdit(host, grant = false)
            }.apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .width(0f)
                    .heightMode(SizeMode.MATCH_PARENT)
            }
            actions.addChild("cycle_perm", host.memberCycleButton)
            actions.addChild("grant", host.memberGrantButton)
            actions.addChild("revoke", host.memberRevokeButton)
            refreshMembersTab(host)
        }
    }

    fun refreshMembersTab(host: MisakaPanelHost) {
        syncSelectedMemberName(host)
        if (host.membersListHostInitialized) {
            rebuildMemberRows(host)
        }
        if (host.membersListLabelInitialized) {
            val empty = host.manageMembers.isEmpty()
            host.membersListLabel.visibility =
                if (empty) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
            host.membersListLabel.text = formatMembersEmptyHint(host)
        }
        if (host.membersAccessLabelInitialized) {
            host.membersAccessLabel.text = formatMembersAccess(host)
            host.membersAccessLabel.alpha = if (host.manageCanEditMembers) 0.78f else 0.9f
        }
        if (host.memberTargetLabelInitialized) {
            host.memberTargetLabel.text = formatMemberTarget(host)
        }
        if (host.membersPermHintLabelInitialized) {
            host.membersPermHintLabel.text = formatSelectedPermission(host)
        }
        if (host.memberEditButtonsInitialized) {
            setButtonLabel(host.memberCycleButton, currentPermissionLabel(host))
        }
        refreshMemberEditControls(host)
    }

    private fun syncSelectedMemberName(host: MisakaPanelHost) {
        val typed = if (memberNameInputReady(host)) {
            host.memberNameInput.text.trim()
        } else {
            ""
        }
        if (typed.isNotEmpty()) {
            host.selectedMemberName = typed
            return
        }
        if (host.selectedMemberName.isNotEmpty()
            && host.manageMembers.none { it.name() == host.selectedMemberName }
        ) {
            host.selectedMemberName = ""
        }
    }

    private fun memberNameInputReady(host: MisakaPanelHost): Boolean {
        return try {
            host.memberNameInput
            true
        } catch (_: UninitializedPropertyAccessException) {
            false
        }
    }

    private fun rebuildMemberRows(host: MisakaPanelHost) {
        host.membersListHost.clearChildren()
        for (row in host.manageMembers) {
            val selected = row.name() == host.selectedMemberName
            val role = if (row.admin()) {
                Component.translatable("screen.academy.misaka_net_role_admin").string
            } else {
                Component.translatable("screen.academy.misaka_net_role_member").string
            }
            val perms = if (row.permissions().isEmpty()) {
                "-"
            } else {
                row.permissions().joinToString(",")
            }
            val button = ButtonWidget().apply {
                background = sisterRowBackground(selected)
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(MisakaNetworkPanelScreen.LIST_ITEM_HEIGHT)
                onClickListener = OnClickListener {
                    host.selectedMemberName = row.name()
                    if (memberNameInputReady(host)) {
                        host.memberNameInput.text = row.name()
                    }
                    refreshMembersTab(host)
                }
                addChild(
                    "cols",
                    memberColumnsRow(row.name(), role, perms, header = false, selected = selected).apply {
                        layoutParams = FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT)
                            .paddingHorizontal(2f)
                            .gravity(Gravity.CENTER_VERTICAL)
                    }
                )
            }
            host.membersListHost.addChild("member_${row.name()}", button)
        }
    }

    private fun refreshMemberEditControls(host: MisakaPanelHost) {
        val canEdit = host.manageCanEditMembers
        val target = memberTargetName(host)
        val hasTarget = target.isNotEmpty()
        val targetingSelf = hasTarget && isLocalPlayerName(target)
        // Select-self is fine for viewing; mutate only other players.
        val canMutate = canEdit && hasTarget && !targetingSelf
        if (host.membersEditorHostInitialized) {
            host.membersEditorHost.alpha = if (canEdit) 1f else 0.55f
        }
        if (memberNameInputReady(host)) {
            host.memberNameInput.isEnabled = canEdit
            host.memberNameInput.alpha = if (canEdit) 1f else 0.4f
        }
        if (!host.memberEditButtonsInitialized) {
            return
        }
        setActionEnabled(host.memberCycleButton, canEdit && !targetingSelf)
        setActionEnabled(host.memberGrantButton, canMutate)
        setActionEnabled(host.memberRevokeButton, canMutate)
    }

    private fun setActionEnabled(button: ButtonWidget, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.35f
        button.background = hostActionBackground(enabled)
    }

    private fun hostActionBackground(enabled: Boolean): StateListDrawable {
        val resting = ColorDrawable(
            if (enabled) MisakaNetworkPanelScreen.ACTION_RESTING_PLANE else 0x22000000
        )
        return StateListDrawable().apply {
            setDefault(resting)
            if (enabled) {
                addState(Widget.HOVERED, ColorDrawable(MisakaNetworkPanelScreen.HOVER_PLANE))
                addState(Widget.FOCUSED, ColorDrawable(MisakaNetworkPanelScreen.HOVER_PLANE))
                addState(Widget.PRESSED, ColorDrawable(MisakaNetworkPanelScreen.SELECTED_PLANE))
            }
            addState(Widget.DISABLED, ColorDrawable(0x18000000))
        }
    }

    private fun setButtonLabel(button: ButtonWidget, text: String) {
        if (!button.children.containsKey("label")) {
            return
        }
        (button.children["label"] as? LabelWidget)?.text = text
    }

    private fun memberTargetName(host: MisakaPanelHost): String {
        val typed = if (memberNameInputReady(host)) host.memberNameInput.text.trim() else ""
        return typed.ifEmpty { host.selectedMemberName.trim() }
    }

    private fun isLocalPlayerName(name: String): Boolean {
        val local = net.minecraft.client.Minecraft.getInstance().player ?: return false
        return local.gameProfile.name().equals(name, ignoreCase = true)
    }

    private fun formatMembersEmptyHint(host: MisakaPanelHost): String {
        return if (host.manageMembers.isEmpty()) {
            Component.translatable("screen.academy.misaka_net_members_empty").string
        } else {
            ""
        }
    }

    private fun formatMembersAccess(host: MisakaPanelHost): String {
        return if (host.manageCanEditMembers) {
            Component.translatable("screen.academy.misaka_net_members_editable").string
        } else {
            Component.translatable("screen.academy.misaka_net_members_readonly").string
        }
    }

    private fun formatMemberTarget(host: MisakaPanelHost): String {
        val name = memberTargetName(host)
        return if (name.isEmpty()) {
            Component.translatable("screen.academy.misaka_net_target_none").string
        } else {
            Component.translatable("screen.academy.misaka_net_perm_target", name).string
        }
    }

    private fun currentPermissionLabel(host: MisakaPanelHost): String {
        val perm = EDITABLE_PERMISSIONS[host.memberPermIndex.coerceIn(0, EDITABLE_PERMISSIONS.lastIndex)]
        return Component.translatable("screen.academy.misaka_net_perm_cycle_value", perm).string
    }

    private fun formatSelectedPermission(host: MisakaPanelHost): String {
        val target = memberTargetName(host)
        return when {
            !host.manageCanEditMembers ->
                Component.translatable("screen.academy.misaka_net_perm_edit_locked").string
            target.isEmpty() ->
                Component.translatable("screen.academy.misaka_net_perm_need_name").string
            isLocalPlayerName(target) ->
                Component.translatable("screen.academy.misaka_net_perm_self_locked").string
            else ->
                Component.translatable("screen.academy.misaka_net_perm_action_hint").string
        }
    }

    private fun sendPermissionEdit(host: MisakaPanelHost, grant: Boolean) {
        if (!host.manageCanEditMembers) {
            return
        }
        val name = memberTargetName(host)
        if (name.isEmpty() || isLocalPlayerName(name)) {
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

    private fun memberColumnsRow(
        name: String,
        role: String,
        perms: String,
        header: Boolean,
        selected: Boolean = false
    ): LinearLayoutWidget {
        val alpha = when {
            header -> 0.55f
            selected -> 1f
            else -> 0.88f
        }
        return LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = MisakaNetworkPanelScreen.SPACING_MICRO
            isClickable = false
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(MisakaNetworkPanelScreen.LIST_ITEM_HEIGHT)
            addChild("name", memberCell(name, 0.42f, alpha, header))
            addChild("role", memberCell(role, 0.22f, alpha, header))
            addChild("perms", memberCell(perms, 0.36f, alpha, header))
        }
    }

    private fun memberCell(text: String, weight: Float, alpha: Float, header: Boolean): LabelWidget {
        return LabelWidget(text).apply {
            scale = if (header) 0.65f else 0.7f
            this.alpha = alpha
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(weight)
                .width(0f)
                .heightMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER_VERTICAL)
        }
    }

    fun buildSistersTab(host: MisakaPanelHost): FrameLayoutWidget {
        // Sticky metrics / header / selection / pager outside the scroll viewport.
        // Only the sister rows scroll — keeps hit targets on a non-zero-height panel.
        return MisakaPanelLayouts.tabColumn {
            host.networkTotalMskLabel = LabelWidget(formatSupply(host)).apply {
                scale = 0.75f
                alpha = 0.82f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(10f)
            }
            addChild("total_msk", host.networkTotalMskLabel)
            host.networkDemandLabel = LabelWidget(formatDemand(host)).apply {
                scale = 0.75f
                alpha = 0.82f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(10f)
            }
            addChild("demand_msk", host.networkDemandLabel)
            host.networkSatisfactionLabel = LabelWidget(formatSatisfaction(host)).apply {
                scale = 0.75f
                alpha = 0.82f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(10f)
            }
            addChild("satisfaction", host.networkSatisfactionLabel)
            addChild("header", sisterColumnsRow(
                Component.translatable("screen.academy.misaka_net_col_serial").string,
                Component.translatable("screen.academy.misaka_net_col_perception").string,
                Component.translatable("screen.academy.misaka_net_col_msk").string,
                Component.translatable("screen.academy.misaka_net_col_node").string,
                Component.translatable("screen.academy.misaka_net_col_coverage").string,
                Component.translatable("screen.academy.misaka_net_col_status").string,
                header = true
            ).also { it.isClickable = false })
            addChild("header_rule", FillWidget(MisakaNetworkPanelScreen.PRIMARY_FOREGROUND).apply {
                alpha = 0.35f
                isClickable = false
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(1f)
            })

            host.emptySistersLabel = LabelWidget(
                Component.translatable("screen.academy.misaka_net_empty").string
            ).apply {
                scale = 0.75f
                alpha = 0.7f
                visibility = Widget.Visibility.GONE
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(MisakaNetworkPanelScreen.LIST_ITEM_HEIGHT)
            }
            addChild("empty", host.emptySistersLabel)

            host.sistersRows = LinearLayoutWidget().apply {
                orientation = Orientation.VERTICAL
                spacing = MisakaNetworkPanelScreen.SPACING_MICRO
                isClickable = false
                layoutParams = FrameLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .heightMode(SizeMode.WRAP_CONTENT)
            }
            addChild(
                "rows_scroll",
                MisakaPanelLayouts.scrollBody {
                    addChild("rows", host.sistersRows)
                }
            )
            refreshSisterRows(host)

            // §7.2 eject: pick a row above, read back who is targeted, then act.
            val selectionRow = LinearLayoutWidget().apply {
                orientation = Orientation.HORIZONTAL
                spacing = MisakaNetworkPanelScreen.SPACING_MINOR
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(MisakaNetworkPanelScreen.INFO_ROW_HEIGHT)
                    .marginTop(MisakaNetworkPanelScreen.SPACING_MINOR)
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
            addChild("selection_row", selectionRow)
            refreshSisterSelection(host)

            val pager = LinearLayoutWidget().apply {
                orientation = Orientation.HORIZONTAL
                spacing = MisakaNetworkPanelScreen.SPACING_MINOR
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(MisakaNetworkPanelScreen.LIST_ITEM_HEIGHT)
            }
            addChild("pager", pager)
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
                val maxPage = if (host.manageTotalCount <= 0) {
                    0
                } else {
                    (host.manageTotalCount - 1) / WirelessForwardingMisakaNAT.MANAGE_PAGE_SIZE
                }
                if (host.managePageIndex < maxPage) {
                    requestManagePage(host, host.managePageIndex + 1)
                }
            }.apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .heightMode(SizeMode.MATCH_PARENT)
            })
        }
    }

    fun refreshSisterRows(host: MisakaPanelHost) {
        if (!host.sistersRowsInitialized) {
            return
        }
        host.sistersRows.clearChildren()
        for (item in host.manageSisters) {
            host.sistersRows.addChild("sister_${item.misakaUuid()}", sisterRow(host, item))
        }
    }

    private fun sisterRow(
        host: MisakaPanelHost,
        item: MisakaNetManageDataPacket.SisterSummary
    ): ButtonWidget {
        val selected = item.misakaUuid() == host.selectedSisterUuid
        val status = when {
            item.incapacitated() ->
                Component.translatable("screen.academy.misaka_net_incapacitated").string
            item.starving() ->
                Component.translatable("screen.academy.misaka_net_starving").string
            else ->
                Component.translatable("screen.academy.misaka_net_status_ok").string
        }
        val coverage = if (item.inCoverage()) {
            Component.translatable("screen.academy.misaka_net_coverage_in").string
        } else {
            Component.translatable("screen.academy.misaka_net_coverage_out").string
        }
        // Whole row is the button so column labels cannot steal presses from an underlay.
        return ButtonWidget().apply {
            background = sisterRowBackground(selected)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(MisakaNetworkPanelScreen.LIST_ITEM_HEIGHT)
            onClickListener = OnClickListener {
                host.selectedSisterUuid =
                    if (item.misakaUuid() == host.selectedSisterUuid) null else item.misakaUuid()
                refreshSisterSelection(host, rebuildRows = true)
            }
            addChild(
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
                    statusAccent = item.incapacitated() || item.starving()
                ).apply {
                    layoutParams = FrameLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT)
                        .paddingHorizontal(2f)
                        .gravity(Gravity.CENTER_VERTICAL)
                }
            )
        }
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
        return host.manageSisters.firstOrNull { it.misakaUuid() == selected }
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
        if (rebuildRows) {
            refreshSisterRows(host)
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
        val root = MisakaPanelLayouts.scrollTab {
            for (sink in MisakaComputeSink.entries) {
                addChild("sink_${sink.id()}", allocRow(host, sink))
            }
            host.allocatedLabel = LabelWidget(
                Component.translatable("screen.academy.misaka_net_allocated", 0).string
            ).apply {
                scale = 0.75f
                alpha = 0.82f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(10f)
                    .marginTop(MisakaNetworkPanelScreen.SPACING_MINOR)
            }
            addChild("allocated", host.allocatedLabel)
        }
        root.visibility = Widget.Visibility.GONE
        root.isEnabled = false
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
