package org.academy.internal.client.gui.screen

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.academy.api.client.gui.event.OnClickListener
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.*
import org.academy.api.client.util.AnimationUtil
import org.academy.internal.common.world.inventory.AerospaceSignalCabinMenu
import org.academy.internal.common.world.level.block.entity.AerospaceSignalCabinBlockEntity
import org.academy.internal.common.world.level.block.entity.AerospaceSignalCabinBlockEntity.ManagedSatRow
import org.academy.internal.common.world.level.block.entity.AerospaceSignalCabinBlockEntity.RebindLaserRow
import org.academy.internal.common.world.level.block.entity.AerospaceSignalCabinBlockEntity.RetargetNetRow

/**
 * Ops tab UI for [AerospaceSignalCabinScreen]: satellite list/detail, force-crash confirm,
 * and related refresh/format helpers. Behavior-frozen extract from the cabin screen shell.
 */
internal class AerospaceCabinOpsUi(
    private val host: Host
) {
    interface Host {
        val blockEntity: AerospaceSignalCabinBlockEntity
        val cabinMenu: AerospaceSignalCabinMenu
        val client: Minecraft
        fun createActionButton(labelKey: String, buttonId: Int): ButtonWidget
        fun createLocalActionButton(labelKey: String, onClick: () -> Unit): ButtonWidget
    }

    private var opsCountSetter: (String) -> Unit = {}
    private var detailTitleSetter: (String) -> Unit = {}
    private var detailBodySetter: (String) -> Unit = {}
    private var detailFeedbackSetter: (String) -> Unit = {}
    private var detailNetworkSetter: (String) -> Unit = {}
    private var detailLaserSetter: (String) -> Unit = {}
    private var satListColumn: LinearLayoutWidget? = null
    private var satListHeader: LinearLayoutWidget? = null
    private var networkListColumn: LinearLayoutWidget? = null
    private var laserListColumn: LinearLayoutWidget? = null
    private var lastSatList: List<ManagedSatRow>? = null
    private var lastNetworkList: List<RetargetNetRow>? = null
    private var lastLaserList: List<RebindLaserRow>? = null
    private var opsListPane: FrameLayoutWidget? = null
    private var opsDetailPane: FrameLayoutWidget? = null
    private var forceConfirmOverlay: FrameLayoutWidget? = null
    private var forceCountdownSetter: (String) -> Unit = {}
    private var forceCountdownLabel: LabelWidget? = null
    private var forceArmButton: ButtonWidget? = null
    private var forceCancelButton: ButtonWidget? = null
    private var viewingDetail: Boolean = false
    private var pendingDetailRow: ManagedSatRow? = null
    private var forceConfirmOpen: Boolean = false
    private var cachedSatColWidths: SatColWidths? = null

    fun onContainerTick() {
        opsCountSetter(opsCountText())
        refreshSatelliteListUi()
        if (viewingDetail) {
            detailTitleSetter(detailTitleText())
            detailBodySetter(detailBodyText())
            detailLaserSetter(detailLaserText())
            detailNetworkSetter(detailNetworkText())
            detailFeedbackSetter(opsFeedbackText())
            refreshForceCrashUi()
            refreshLaserListUi()
            refreshNetworkListUi()
        }
    }

    fun createOpsPage(): FrameLayoutWidget {
        val page = FrameLayoutWidget()
        page.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)

        val listPane = createOpsListPane()
        val detailPane = createOpsDetailPane()
        detailPane.visibility = Widget.Visibility.GONE
        detailPane.isEnabled = false

        val confirmOverlay = createForceCrashConfirmOverlay()
        confirmOverlay.visibility = Widget.Visibility.GONE
        confirmOverlay.isEnabled = false

        page.addChild("list", listPane)
        page.addChild("detail", detailPane)
        page.addChild("force_confirm", confirmOverlay)
        opsListPane = listPane
        opsDetailPane = detailPane
        forceConfirmOverlay = confirmOverlay
        refreshSatelliteListUi(force = true)
        return page
    }

    fun createForceCrashConfirmOverlay(): FrameLayoutWidget {
        val overlay = FrameLayoutWidget()
        overlay.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)

        overlay.addChild("dim", BlendQuadWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            alpha = 0.72f
        })

        val panel = FrameLayoutWidget()
        panel.layoutParams = FrameLayoutWidget.LayoutParams()
            .width(160f)
            .height(96f)
            .gravity(Gravity.CENTER)

        panel.addChild("back", BlendQuadWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            alpha = 0.55f
        })

        val column = LinearLayoutWidget()
        column.orientation = Orientation.VERTICAL
        column.spacing = 4f
        column.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)
            .margin(10f, 10f, 10f, 10f)

        column.addChild("title", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.ops_force_crash_confirm_title").string).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(14f)
            scale = 0.85f
        })

        column.addChild("body", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.ops_force_crash_confirm_body").string).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(36f)
            scale = 0.7f
            alpha = 0.92f
        })

        val actions = LinearLayoutWidget()
        actions.orientation = Orientation.HORIZONTAL
        actions.spacing = 8f
        actions.layoutParams = LinearLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(18f)
            .margin(0f, 4f, 0f, 0f)

        actions.addChild("dismiss", host.createLocalActionButton("gui.academy.aerospace_signal_cabin.ops_force_crash_confirm_no") {
            closeForceCrashConfirm()
        }.apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .height(18f)
        })

        actions.addChild("confirm", host.createLocalActionButton("gui.academy.aerospace_signal_cabin.ops_force_crash_confirm_yes") {
            closeForceCrashConfirm()
            host.client.gameMode?.handleInventoryButtonClick(
                host.cabinMenu.containerId,
                AerospaceSignalCabinMenu.BUTTON_FORCE_CRASH
            )
        }.apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .height(18f)
        })

        column.addChild("actions", actions)
        panel.addChild("column", column)
        overlay.addChild("panel", panel)
        return overlay
    }

    fun createOpsListPane(): FrameLayoutWidget {
        val pane = FrameLayoutWidget()
        pane.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)

        pane.addChild("back", BlendQuadWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            alpha = 0.5f
        })

        val column = LinearLayoutWidget()
        column.orientation = Orientation.VERTICAL
        column.spacing = 2f
        column.layoutParams = FrameLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .heightMode(SizeMode.MATCH_PARENT)
            .gravity(Gravity.TOP)
            .margin(6f, 6f, 6f, 6f)

        column.addChild("title", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_title").string).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(12f)
            scale = SCALE_TITLE
        })

        column.addChild("list_hint", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_hint").string).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(18f)
            scale = SCALE_SECTION
            alpha = 0.72f
        })

        val countLabel = LabelWidget(opsCountText()).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
            scale = SCALE_BODY
        }
        opsCountSetter = { countLabel.text = it }
        column.addChild("count", countLabel)

        val header = satelliteColumnsRow(
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_sat_col_index").string,
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_sat_col_dim").string,
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_sat_col_phase").string,
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_sat_col_power").string,
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_sat_col_kind").string,
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_sat_col_id").string,
            header = true
        ).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(11f)
                .margin(0f, 2f, 0f, 0f)
        }
        satListHeader = header
        column.addChild("header", header)

        val listScroll = ScrollPanelWidget(Orientation.VERTICAL)
        listScroll.layoutParams = LinearLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(110f)
            .margin(0f, 2f, 0f, 0f)
        val listColumn = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = 2f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .heightMode(SizeMode.WRAP_CONTENT)
        }
        satListColumn = listColumn
        listScroll.addChild("content", listColumn)
        column.addChild("sat_list", listScroll)

        pane.addChild("column", column)
        return pane
    }

    fun createOpsDetailPane(): FrameLayoutWidget {
        val pane = FrameLayoutWidget()
        pane.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)

        pane.addChild("back", BlendQuadWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            alpha = 0.5f
        })

        // Dense instrument column: fixed header + two weighted pick lists fill remaining space.
        val column = LinearLayoutWidget()
        column.orientation = Orientation.VERTICAL
        column.spacing = DETAIL_SPACING
        column.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)
            .margin(6f, 6f, 6f, 6f)

        val topBar = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = 4f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(14f)
        }
        topBar.addChild("back_btn", host.createLocalActionButton("gui.academy.aerospace_signal_cabin.ops_back") {
            closeOpsDetail()
        }.apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(44f)
                .height(14f)
        })
        val title = LabelWidget(detailTitleText()).apply {
            scale = SCALE_TITLE
            alpha = 0.95f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(12f)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        detailTitleSetter = { title.text = it }
        topBar.addChild("title", title)
        column.addChild("top_bar", topBar)

        val status = LabelWidget(detailBodyText()).apply {
            scale = SCALE_BODY
            alpha = 0.88f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
        }
        detailBodySetter = { status.text = it }
        column.addChild("status", status)

        val laserStatus = LabelWidget(detailLaserText()).apply {
            scale = SCALE_BODY
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
        }
        detailLaserSetter = { laserStatus.text = it }
        column.addChild("laser_status", laserStatus)

        val forceCountdown = LabelWidget("").apply {
            scale = SCALE_BODY
            alpha = 0.9f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
            visibility = Widget.Visibility.GONE
        }
        forceCountdownSetter = { forceCountdown.text = it }
        forceCountdownLabel = forceCountdown
        column.addChild("force_countdown", forceCountdown)

        val forceActions = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = 4f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(14f)
        }
        val forceArm = host.createLocalActionButton("gui.academy.aerospace_signal_cabin.ops_force_crash") {
            openForceCrashConfirm()
        }.apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(72f)
                .height(14f)
        }
        forceArmButton = forceArm
        forceActions.addChild("force_crash", forceArm)

        val forceCancel = host.createActionButton(
            "gui.academy.aerospace_signal_cabin.ops_force_crash_cancel",
            AerospaceSignalCabinMenu.BUTTON_CANCEL_FORCE_CRASH
        ).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(72f)
                .height(14f)
            visibility = Widget.Visibility.GONE
            isEnabled = false
        }
        forceCancelButton = forceCancel
        forceActions.addChild("force_crash_cancel", forceCancel)
        column.addChild("force_actions", forceActions)

        val feedback = LabelWidget(opsFeedbackText()).apply {
            scale = SCALE_BODY
            alpha = 0.78f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
        }
        detailFeedbackSetter = { feedback.text = it }
        column.addChild("feedback", feedback)

        column.addChild(
            "laser_section",
            detailSectionLabel("gui.academy.aerospace_signal_cabin.ops_laser_pick")
        )
        column.addChild(
            "laser_list",
            detailPickListHost { laserListColumn = it }
        )

        val networkLabel = LabelWidget(detailNetworkText()).apply {
            scale = SCALE_BODY
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
                .margin(0f, 2f, 0f, 0f)
        }
        detailNetworkSetter = { networkLabel.text = it }
        column.addChild("network_current", networkLabel)

        column.addChild(
            "network_section",
            detailSectionLabel("gui.academy.aerospace_signal_cabin.ops_network_pick")
        )
        column.addChild(
            "network_list",
            detailPickListHost { networkListColumn = it }
        )

        pane.addChild("column", column)
        return pane
    }

    fun detailSectionLabel(labelKey: String): LabelWidget {
        return LabelWidget(Component.translatable(labelKey).string).apply {
            scale = SCALE_SECTION
            alpha = 0.7f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
                .margin(0f, 2f, 0f, 0f)
        }
    }

    fun detailPickListHost(bindColumn: (LinearLayoutWidget) -> Unit): FrameLayoutWidget {
        val host = FrameLayoutWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .height(0f)
                .margin(0f, 1f, 0f, 0f)
        }
        host.addChild("frame", BlendQuadWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            alpha = 0.22f
        })
        val scroll = ScrollPanelWidget(Orientation.VERTICAL).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .margin(2f, 2f, 2f, 2f)
        }
        val listColumn = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = 1f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .heightMode(SizeMode.WRAP_CONTENT)
        }
        bindColumn(listColumn)
        scroll.setContent(listColumn)
        host.addChild("scroll", scroll)
        return host
    }

    fun openOpsDetail(row: ManagedSatRow) {
        pendingDetailRow = row
        host.client.gameMode?.handleInventoryButtonClick(
            host.cabinMenu.containerId,
            AerospaceSignalCabinMenu.BUTTON_SELECT_SAT_BASE + row.index
        )
        val list = opsListPane ?: return
        val detail = opsDetailPane ?: return
        viewingDetail = true
        AnimationUtil.hide(list)
        AnimationUtil.show(detail)
        detailTitleSetter(detailTitleText())
        detailBodySetter(detailBodyText())
        detailLaserSetter(detailLaserText())
        detailNetworkSetter(detailNetworkText())
        detailFeedbackSetter(opsFeedbackText())
        refreshForceCrashUi()
        refreshLaserListUi(force = true)
        refreshNetworkListUi(force = true)
    }

    fun closeOpsDetail(immediate: Boolean = false) {
        pendingDetailRow = null
        closeForceCrashConfirm(immediate = true)
        val list = opsListPane ?: return
        val detail = opsDetailPane ?: return
        viewingDetail = false
        if (immediate) {
            detail.cancelAnimations()
            list.cancelAnimations()
            detail.visibility = Widget.Visibility.GONE
            detail.isEnabled = false
            detail.alpha = 0f
            list.visibility = Widget.Visibility.VISIBLE
            list.isEnabled = true
            list.alpha = 1f
            list.translationY = 0f
        } else {
            AnimationUtil.hide(detail)
            AnimationUtil.show(list)
        }
    }

    fun openForceCrashConfirm() {
        val overlay = forceConfirmOverlay ?: return
        forceConfirmOpen = true
        AnimationUtil.show(overlay)
    }

    fun closeForceCrashConfirm(immediate: Boolean = false) {
        val overlay = forceConfirmOverlay ?: return
        forceConfirmOpen = false
        if (immediate) {
            overlay.cancelAnimations()
            overlay.visibility = Widget.Visibility.GONE
            overlay.isEnabled = false
            overlay.alpha = 0f
        } else {
            AnimationUtil.hide(overlay)
        }
    }

    fun refreshForceCrashUi() {
        val row = selectedSatRow()
        val armed = row != null && row.forceCrashTicks > 0 && row.phase != "CRASHING"
        val canArm = row != null && row.phase != "CRASHING" && row.forceCrashTicks <= 0
        val remainingSec = if (row != null && row.forceCrashTicks > 0) {
            (row.forceCrashTicks + 19) / 20
        } else {
            0
        }
        forceCountdownSetter(
            if (armed) {
                Component.translatable(
                    "gui.academy.aerospace_signal_cabin.ops_force_crash_countdown",
                    remainingSec
                ).string
            } else {
                ""
            }
        )
        forceCountdownLabel?.let {
            it.visibility = if (armed) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
        }
        forceArmButton?.let {
            it.visibility = if (canArm) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
            it.isEnabled = canArm
        }
        forceCancelButton?.let {
            it.visibility = if (armed) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
            it.isEnabled = armed
        }
        if (armed && forceConfirmOpen) {
            closeForceCrashConfirm(immediate = true)
        }
    }

    fun refreshSatelliteListUi(force: Boolean = false) {
        val column = satListColumn ?: return
        val rows = host.blockEntity.managedSatelliteList
        if (!force && rows == lastSatList) {
            return
        }
        lastSatList = rows
        column.clearChildren()
        val hasRows = rows.isNotEmpty()
        satListHeader?.visibility = if (hasRows) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
        if (!hasRows) {
            column.addChild("empty", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.ops_sat_none").string).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(16f)
                scale = 0.75f
                alpha = 0.85f
            })
            return
        }
        rows.forEachIndexed { rowIndex, parsed ->
            if (rowIndex >= AerospaceSignalCabinMenu.BUTTON_SELECT_SAT_MAX) {
                return@forEachIndexed
            }
            val power = if (parsed.powered) {
                Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_power_on").string
            } else {
                Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_power_off").string
            }
            val kind = if (parsed.hyper) {
                Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_kind_hyper").string
            } else {
                Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_kind_normal").string
            }
            val columns = satelliteColumnsRow(
                parsed.index.toString(),
                formatDimPathShort(parsed.dimPath),
                phaseLabelShort(parsed.phase),
                power,
                kind,
                parsed.id8.take(ID_DISPLAY_LEN),
                header = false
            )
            val button = ButtonWidget()
            button.onClickListener = OnClickListener { openOpsDetail(parsed) }
            button.addChild("back", BlendQuadWidget().apply {
                layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
                alpha = 0.35f
            })
            button.addChild("cols", columns.apply {
                layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                    .gravity(Gravity.CENTER_VERTICAL)
                    .margin(2f, 0f, 2f, 0f)
            })
            button.layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(16f)
            column.addChild("sat_${parsed.index}", button)
        }
    }

    fun satelliteColumnsRow(
        index: String,
        dim: String,
        phase: String,
        power: String,
        kind: String,
        id: String,
        header: Boolean
    ): LinearLayoutWidget {
        val cols = satListColWidths()
        val row = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = COL_SPACING
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(if (header) 11f else 12f)
        }
        fun cell(text: String, width: Float): LabelWidget = LabelWidget(text).apply {
            scale = 1f
            baseFontSize = LIST_FONT_SIZE
            alpha = if (header) 0.62f else 0.92f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(width)
                .height(10f)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        row.addChild("index", cell(index, cols.index))
        row.addChild("dim", cell(dim, cols.dim))
        row.addChild("phase", cell(phase, cols.phase))
        row.addChild("power", cell(power, cols.power))
        row.addChild("kind", cell(kind, cols.kind))
        row.addChild("id", cell(id, cols.id))
        return row
    }

    /**
     * Measure each column's content need, then share [LIST_ROW_BUDGET] by need ratio
     * (extra space spreads out when under budget; over budget shrinks proportionally).
     */
    fun satListColWidths(): SatColWidths {
        cachedSatColWidths?.let { return it }
        fun need(vararg samples: String): Float {
            var max = 0f
            for (sample in samples) {
                max = maxOf(max, LabelWidget.getTextWidth(sample, LIST_FONT_SIZE))
            }
            return kotlin.math.ceil(max.toDouble()).toFloat() + COL_PAD
        }
        fun t(key: String): String = Component.translatable(key).string
        val needs = floatArrayOf(
            need(
                t("gui.academy.aerospace_signal_cabin.ops_sat_col_index"),
                AerospaceSignalCabinMenu.BUTTON_SELECT_SAT_MAX.toString()
            ),
            need(
                t("gui.academy.aerospace_signal_cabin.ops_sat_col_dim"),
                t("gui.academy.aerospace_signal_cabin.ops_dim_short_overworld"),
                t("gui.academy.aerospace_signal_cabin.ops_dim_short_nether"),
                t("gui.academy.aerospace_signal_cabin.ops_dim_short_end")
            ),
            need(
                t("gui.academy.aerospace_signal_cabin.ops_sat_col_phase"),
                t("gui.academy.aerospace_signal_cabin.ops_list_phase_orbit"),
                t("gui.academy.aerospace_signal_cabin.ops_list_phase_launch"),
                t("gui.academy.aerospace_signal_cabin.ops_list_phase_crash")
            ),
            need(
                t("gui.academy.aerospace_signal_cabin.ops_sat_col_power"),
                t("gui.academy.aerospace_signal_cabin.ops_list_power_on"),
                t("gui.academy.aerospace_signal_cabin.ops_list_power_off")
            ),
            need(
                t("gui.academy.aerospace_signal_cabin.ops_sat_col_kind"),
                t("gui.academy.aerospace_signal_cabin.ops_list_kind_hyper"),
                t("gui.academy.aerospace_signal_cabin.ops_list_kind_normal")
            ),
            need(
                t("gui.academy.aerospace_signal_cabin.ops_sat_col_id"),
                "f".repeat(ID_DISPLAY_LEN)
            )
        )
        val gaps = COL_SPACING * (needs.size - 1)
        val available = (LIST_ROW_BUDGET - gaps).coerceAtLeast(needs.size.toFloat())
        val widths = allocateByNeed(needs, available)
        val fitted = SatColWidths(
            index = widths[0],
            dim = widths[1],
            phase = widths[2],
            power = widths[3],
            kind = widths[4],
            id = widths[5]
        )
        cachedSatColWidths = fitted
        return fitted
    }

    fun refreshNetworkListUi(force: Boolean = false) {
        val column = networkListColumn ?: return
        val rows = host.blockEntity.retargetNetworkList
        if (!force && rows == lastNetworkList) {
            return
        }
        lastNetworkList = rows
        column.clearChildren()
        if (rows.isEmpty()) {
            column.addChild("empty", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.ops_no_networks").string).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(16f)
                scale = 0.75f
                alpha = 0.85f
            })
            return
        }
        rows.forEachIndexed { row, entry ->
            if (row >= AerospaceSignalCabinMenu.BUTTON_RETARGET_NET_MAX) {
                return@forEachIndexed
            }
            val name = entry.nodeName
            val current = entry.isCurrentSatNetwork
            val label = if (current) {
                Component.translatable("gui.academy.aerospace_signal_cabin.ops_network_row_current", name).string
            } else {
                Component.translatable("gui.academy.aerospace_signal_cabin.ops_network_row", name).string
            }
            column.addChild(
                "net_$row",
                createOpsListSelectButton(
                    label = label,
                    backAlpha = if (current) 0.5f else 0.35f,
                    buttonId = AerospaceSignalCabinMenu.BUTTON_RETARGET_NET_BASE + row
                )
            )
        }
    }

    fun refreshLaserListUi(force: Boolean = false) {
        val column = laserListColumn ?: return
        val rows = host.blockEntity.rebindLaserList
        if (!force && rows == lastLaserList) {
            return
        }
        lastLaserList = rows
        column.clearChildren()
        if (rows.isEmpty()) {
            column.addChild("empty", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.ops_no_lasers").string).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(16f)
                scale = 0.75f
                alpha = 0.85f
            })
            return
        }
        rows.forEachIndexed { row, entry ->
            if (row >= AerospaceSignalCabinMenu.BUTTON_REBIND_LASER_MAX) {
                return@forEachIndexed
            }
            val pos = entry.pos
            val coords = "${pos.x},${pos.y},${pos.z}"
            val ready = entry.ready
            val label = if (ready) {
                Component.translatable("gui.academy.aerospace_signal_cabin.ops_laser_row_ready", coords).string
            } else {
                Component.translatable("gui.academy.aerospace_signal_cabin.ops_laser_row_unready", coords).string
            }
            column.addChild(
                "laser_$row",
                createOpsListSelectButton(
                    label = label,
                    backAlpha = if (ready) 0.45f else 0.3f,
                    buttonId = AerospaceSignalCabinMenu.BUTTON_REBIND_LASER_BASE + row
                )
            )
        }
    }

    fun selectedSatRow(): ManagedSatRow? {
        val selected = host.blockEntity.selectedSatelliteIndex
        val pending = pendingDetailRow
        val rows = host.blockEntity.managedSatelliteList
        if (pending != null) {
            val synced = rows.firstOrNull { it.index == pending.index }
            if (synced != null) {
                if (selected == pending.index) {
                    pendingDetailRow = null
                }
                return synced
            }
            return pending
        }
        return rows.firstOrNull { it.index == selected }
    }

    fun phaseLabel(phase: String): String {
        return when (phase) {
            "ORBIT" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_phase_orbit").string
            "LAUNCHING" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_phase_launch").string
            "CRASHING" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_phase_crash").string
            else -> phase
        }
    }

    fun phaseLabelShort(phase: String): String {
        return when (phase) {
            "ORBIT" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_phase_orbit").string
            "LAUNCHING" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_phase_launch").string
            "CRASHING" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_phase_crash").string
            else -> phase
        }
    }

    fun formatDimPathShort(path: String): String {
        val key = when (path) {
            "overworld" -> "gui.academy.aerospace_signal_cabin.ops_dim_short_overworld"
            "the_nether" -> "gui.academy.aerospace_signal_cabin.ops_dim_short_nether"
            "the_end" -> "gui.academy.aerospace_signal_cabin.ops_dim_short_end"
            else -> null
        }
        return if (key != null) {
            Component.translatable(key).string
        } else {
            path.take(4)
        }
    }

    fun formatDimPathFull(path: String): String = Companion.formatDimPathFull(path)


    fun opsCountText(): String {
        val total = host.blockEntity.managedSatelliteCount
        return if (total <= 0) {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_sat_none").string
        } else {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_sat_count", total).string
        }
    }

    fun detailTitleText(): String {
        val row = selectedSatRow()
        return if (row == null) {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_detail_missing").string
        } else {
            Component.translatable(
                "gui.academy.aerospace_signal_cabin.ops_detail_title",
                row.index,
                row.id8
            ).string
        }
    }

    fun detailNetworkText(): String {
        val name = host.blockEntity.selectedSatNetworkName
        return if (name.isNullOrBlank()) {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_network_unknown").string
        } else {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_network_current", name).string
        }
    }

    fun detailLaserText(): String {
        val row = selectedSatRow()
            ?: return Component.translatable("gui.academy.aerospace_signal_cabin.ops_detail_missing").string
        if (row.laserBound && row.powered) {
            if (row.unpoweredTicks > 0) {
                val debtSec = (row.unpoweredTicks + 19) / 20
                return Component.translatable(
                    "gui.academy.aerospace_signal_cabin.ops_laser_recovering",
                    debtSec
                ).string
            }
            return Component.translatable("gui.academy.aerospace_signal_cabin.ops_laser_bound").string
        }
        if (!row.laserBound) {
            val remainingTicks = (row.crashTimeout - row.unpoweredTicks).coerceAtLeast(0)
            val remainingSec = (remainingTicks + 19) / 20
            return Component.translatable(
                "gui.academy.aerospace_signal_cabin.ops_laser_unbound_countdown",
                remainingSec
            ).string
        }
        // Laser still bound but not feeding (sky/power failure) — same countdown.
        val remainingTicks = (row.crashTimeout - row.unpoweredTicks).coerceAtLeast(0)
        val remainingSec = (remainingTicks + 19) / 20
        return Component.translatable(
            "gui.academy.aerospace_signal_cabin.ops_laser_unpowered_countdown",
            remainingSec
        ).string
    }

    fun detailBodyText(): String {
        val row = selectedSatRow() ?: return Component.translatable("gui.academy.aerospace_signal_cabin.ops_detail_missing").string
        val power = if (row.powered) {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_power_on").string
        } else {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_power_off").string
        }
        val kind = if (row.hyper) {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_kind_hyper").string
        } else {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_kind_normal").string
        }
        return Component.translatable(
            "gui.academy.aerospace_signal_cabin.ops_detail_body",
            formatDimPathFull(row.dimPath),
            phaseLabel(row.phase),
            power,
            kind
        ).string
    }

    fun opsFeedbackText(): String {
        val key = host.blockEntity.opsFeedbackKey
        return if (key.isNullOrEmpty()) {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_detail_idle").string
        } else {
            Component.translatable(key).string
        }
    }

    /** Shared row for ops network / laser select lists (left-aligned label, inventory button id). */
    fun createOpsListSelectButton(label: String, backAlpha: Float, buttonId: Int): ButtonWidget {
        val button = ButtonWidget()
        button.onClickListener = OnClickListener {
            host.client.gameMode?.handleInventoryButtonClick(host.cabinMenu.containerId, buttonId)
        }
        button.addChild("back", BlendQuadWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            alpha = backAlpha
        })
        button.addChild("label", LabelWidget(label).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER_LEFT)
                .margin(4f, 0f, 4f, 0f)
            scale = SCALE_SECTION
        })
        button.layoutParams = LinearLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(14f)
        return button
    }


    companion object {
        const val SCALE_TITLE = 0.8f
        const val SCALE_BODY = 0.75f
        const val SCALE_SECTION = 0.7f
        const val DETAIL_SPACING = 2f
        const val LIST_FONT_SIZE = 7f
        const val COL_SPACING = 3f
        const val COL_PAD = 2f
        const val ID_DISPLAY_LEN = 4
        /** 176 face − 6×2 pane margin − 2×2 row inset. */
        const val LIST_ROW_BUDGET = 156f

        data class SatColWidths(
            val index: Float,
            val dim: Float,
            val phase: Float,
            val power: Float,
            val kind: Float,
            val id: Float
        )

        /** Share [available] across columns in proportion to each column's content need. */
        fun allocateByNeed(needs: FloatArray, available: Float): FloatArray {
            val sum = needs.sum().coerceAtLeast(0.001f)
            val widths = FloatArray(needs.size)
            var used = 0f
            for (i in needs.indices) {
                widths[i] = kotlin.math.floor((available * needs[i] / sum).toDouble()).toFloat()
                    .coerceAtLeast(1f)
                used += widths[i]
            }
            var rem = available - used
            if (rem == 0f) {
                return widths
            }
            // Distribute leftover / reclaim overflow by need rank (prefer wider content columns).
            val order = needs.indices.sortedByDescending { needs[it] }
            var step = 0
            while (rem != 0f && step < needs.size * 8) {
                val i = order[step % order.size]
                if (rem > 0f) {
                    widths[i] += 1f
                    rem -= 1f
                } else if (widths[i] > 1f) {
                    widths[i] -= 1f
                    rem += 1f
                }
                step++
            }
            return widths
        }

        fun formatDimPathFull(path: String): String {
            val key = when (path) {
                "overworld" -> "gui.academy.aerospace_signal_cabin.ops_dim_full_overworld"
                "the_nether" -> "gui.academy.aerospace_signal_cabin.ops_dim_full_nether"
                "the_end" -> "gui.academy.aerospace_signal_cabin.ops_dim_full_end"
                else -> null
            }
            return if (key != null) {
                Component.translatable(key).string
            } else {
                path
            }
        }
    }
}
