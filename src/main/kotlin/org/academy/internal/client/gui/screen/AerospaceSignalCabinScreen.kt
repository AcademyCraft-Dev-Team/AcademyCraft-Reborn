package org.academy.internal.client.gui.screen

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.event.OnClickListener
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.screen.ContainerUiScreen
import org.academy.api.client.gui.util.WirelessPanelUtil.create
import org.academy.api.client.gui.widget.*
import org.academy.api.client.resources.R
import org.academy.api.client.util.AnimationUtil
import org.academy.internal.common.world.inventory.AerospaceSignalCabinMenu
import org.academy.internal.common.world.item.NetworkRelaySatelliteItem
import org.academy.internal.common.world.level.block.entity.AerospaceSignalCabinBlockEntity

class AerospaceSignalCabinScreen private constructor(
    menu: AerospaceSignalCabinMenu,
    playerInventory: Inventory,
    title: Component,
    private val blockEntity: AerospaceSignalCabinBlockEntity
) : ContainerUiScreen<AerospaceSignalCabinMenu>(menu, playerInventory, title) {
    private val mainPos: BlockPos = blockEntity.blockPos
    private var laserLabelSetter: (String) -> Unit = {}
    private var dimLabelSetter: (String) -> Unit = {}
    private var launchFeedbackSetter: (String) -> Unit = {}
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
    private var lastSatListBlob: String? = null
    private var lastNetworkListBlob: String? = null
    private var lastLaserListBlob: String? = null
    private var opsListPane: FrameLayoutWidget? = null
    private var opsDetailPane: FrameLayoutWidget? = null
    private var forceConfirmOverlay: FrameLayoutWidget? = null
    private var forceCountdownSetter: (String) -> Unit = {}
    private var forceCountdownLabel: LabelWidget? = null
    private var forceArmButton: ButtonWidget? = null
    private var forceCancelButton: ButtonWidget? = null
    private var viewingDetail: Boolean = false
    private var pendingDetailRow: SatRow? = null
    private var forceConfirmOpen: Boolean = false
    private var cachedSatColWidths: SatColWidths? = null

    override fun onInit(
        pageButtons: RadioGroupWidget,
        invButton: RadioButtonWidget,
        content: FrameLayoutWidget,
        invPage: FrameLayoutWidget
    ) {
        val duration = 600L
        val childDuration = duration - 100

        val hint = LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.hint").string).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(12f)
                .gravity(Gravity.TOP)
                .margin(8f, 2f, 8f, 0f)
            scale = 0.75f
        }
        invPage.addChild("hint", hint)
        playOpenReveal(hint, 1f, duration, childDuration)

        val laserLabel = LabelWidget(laserSelectionText()).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(12f)
                .gravity(Gravity.TOP)
                .margin(8f, 16f, 8f, 0f)
            scale = 0.75f
        }
        laserLabelSetter = { laserLabel.text = it }
        invPage.addChild("laser_label", laserLabel)
        playOpenReveal(laserLabel, 1f, duration, childDuration)

        val dimLabel = LabelWidget(launchDimText()).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(12f)
                .gravity(Gravity.TOP)
                .margin(8f, 30f, 8f, 0f)
            scale = 0.75f
        }
        dimLabelSetter = { dimLabel.text = it }
        invPage.addChild("dim_label", dimLabel)
        playOpenReveal(dimLabel, 1f, duration, childDuration)

        val launchFeedback = LabelWidget(launchFeedbackText()).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
                .gravity(Gravity.TOP)
                .margin(8f, 42f, 8f, 0f)
            scale = 0.7f
        }
        launchFeedbackSetter = { launchFeedback.text = it }
        invPage.addChild("launch_feedback", launchFeedback)
        playOpenReveal(launchFeedback, 0.9f, duration, childDuration)

        val cycleLaserBtn = createActionButton(
            "gui.academy.aerospace_signal_cabin.cycle_laser",
            AerospaceSignalCabinMenu.BUTTON_CYCLE_LASER
        ).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .width(72f)
                .height(16f)
                .gravity(Gravity.TOP_LEFT)
                .margin(8f, 54f, 0f, 0f)
        }
        invPage.addChild("cycle_laser", cycleLaserBtn)
        playOpenReveal(cycleLaserBtn, 1f, duration, childDuration)

        val launchBtn = createActionButton(
            "gui.academy.aerospace_signal_cabin.launch",
            AerospaceSignalCabinMenu.BUTTON_LAUNCH
        ).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .width(72f)
                .height(16f)
                .gravity(Gravity.TOP_RIGHT)
                .margin(0f, 54f, 8f, 0f)
        }
        invPage.addChild("launch", launchBtn)
        playOpenReveal(launchBtn, 1f, duration, childDuration)

        // Keep above player inventory row (slots start at y=84).
        val cycleDimBtn = createActionButton(
            "gui.academy.aerospace_signal_cabin.cycle_dim",
            AerospaceSignalCabinMenu.BUTTON_CYCLE_DIM
        ).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .width(96f)
                .height(14f)
                .gravity(Gravity.CENTER_TOP)
                .margin(0f, 72f, 0f, 0f)
        }
        invPage.addChild("cycle_dim", cycleDimBtn)
        playOpenReveal(cycleDimBtn, 1f, duration, childDuration)

        val opsPage = createOpsPage()
        opsPage.visibility = Widget.Visibility.GONE
        opsPage.isEnabled = false
        content.addChild("page_ops", opsPage)

        val wirelessPage = create(mainPos, true)
        wirelessPage.visibility = Widget.Visibility.GONE
        wirelessPage.isEnabled = false
        content.addChild("page_wireless", wirelessPage)

        val opsButton = createButton(R.textures.gui.icon.icon_settings)
        opsButton.layoutParams = WidgetContainer.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(16f)
        pageButtons.addChild("ops", opsButton)

        val wirelessButton = createButton(R.textures.gui.icon.icon_wireless)
        wirelessButton.layoutParams = WidgetContainer.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(16f)
        pageButtons.addChild("wireless", wirelessButton)

        pageButtons.onSelectionChanged = {
            when (it.name) {
                "inv" -> {
                    closeOpsDetail(immediate = true)
                    AnimationUtil.hide(opsPage)
                    AnimationUtil.hide(wirelessPage)
                    AnimationUtil.show(invPage)
                    isHandleContainer = true
                    isRenderInventory = true
                }
                "ops" -> {
                    closeOpsDetail(immediate = true)
                    AnimationUtil.hide(invPage)
                    AnimationUtil.hide(wirelessPage)
                    AnimationUtil.show(opsPage)
                    isHandleContainer = false
                    isRenderInventory = false
                }
                "wireless" -> {
                    closeOpsDetail(immediate = true)
                    AnimationUtil.hide(invPage)
                    AnimationUtil.hide(opsPage)
                    AnimationUtil.show(wirelessPage)
                    isHandleContainer = false
                    isRenderInventory = false
                }
            }
        }
        pageButtons.selectButton(invButton)
        playOpenReveal(pageButtons, 1f, duration, childDuration)
    }

    /** Same open timing as the left page rail: alpha fade + slide up with the face expand. */
    private fun playOpenReveal(
        widget: Widget,
        targetAlpha: Float,
        duration: Long,
        childDuration: Long
    ) {
        val endAlpha = targetAlpha.coerceIn(0f, 1f)
        widget.alpha = 0f
        widget.translationY = 20f
        widget.startAnimation(
            ObjectAnimator.ofFloat({ widget.alpha = it }, 0f, endAlpha).setDuration(childDuration)
        )
        widget.startAnimation(
            ObjectAnimator.ofFloat({ widget.translationY = it }, 20f, 0f)
                .setDuration(duration)
                .setInterpolator(EasingFunctions.EASE_OUT_CUBIC)
        )
    }

    override fun containerTick() {
        super.containerTick()
        laserLabelSetter(laserSelectionText())
        dimLabelSetter(launchDimText())
        launchFeedbackSetter(launchFeedbackText())
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

    private fun launchDimText(): String {
        val stack = menu.getSlot(0).item
        if (!NetworkRelaySatelliteItem.isSatellite(stack)) {
            return Component.translatable("gui.academy.aerospace_signal_cabin.dim_idle").string
        }
        if (!NetworkRelaySatelliteItem.isHyper(stack)) {
            return Component.translatable("gui.academy.aerospace_signal_cabin.dim_overworld_only").string
        }
        val path = NetworkRelaySatelliteItem.targetDimension(stack).identifier().path
        return Component.translatable(
            "gui.academy.aerospace_signal_cabin.dim_target",
            formatDimPathFull(path)
        ).string
    }

    private fun launchFeedbackText(): String {
        val key = blockEntity.opsFeedbackKey
        return if (key.isNullOrEmpty()) {
            ""
        } else {
            Component.translatable(key).string
        }
    }

    private fun laserSelectionText(): String {
        if (blockEntity.connectedNodePosition == null) {
            return Component.translatable("gui.academy.aerospace_signal_cabin.laser_need_wireless").string
        }
        val pos = blockEntity.selectedLaserPos
        if (pos == null) {
            return Component.translatable(
                "gui.academy.aerospace_signal_cabin.laser_none",
                blockEntity.selectableLaserCount
            ).string
        }
        val tower = minecraft.level?.getBlockEntity(pos) as? org.academy.internal.common.world.level.block.entity.EnergyLaserTowerBlockEntity
        val key = when {
            tower == null -> "gui.academy.aerospace_signal_cabin.laser_selected_unready"
            !tower.hasClearSky() -> "gui.academy.aerospace_signal_cabin.laser_selected_blocked"
            tower.energyStored <= 0 -> "gui.academy.aerospace_signal_cabin.laser_selected_nopower"
            else -> "gui.academy.aerospace_signal_cabin.laser_selected_ready"
        }
        return Component.translatable(key, pos.x, pos.y, pos.z).string
    }

    private fun createOpsPage(): FrameLayoutWidget {
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

    private fun createForceCrashConfirmOverlay(): FrameLayoutWidget {
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

        actions.addChild("dismiss", createLocalActionButton("gui.academy.aerospace_signal_cabin.ops_force_crash_confirm_no") {
            closeForceCrashConfirm()
        }.apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .height(18f)
        })

        actions.addChild("confirm", createLocalActionButton("gui.academy.aerospace_signal_cabin.ops_force_crash_confirm_yes") {
            closeForceCrashConfirm()
            minecraft.gameMode?.handleInventoryButtonClick(
                menu.containerId,
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

    private fun createOpsListPane(): FrameLayoutWidget {
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

    private fun createOpsDetailPane(): FrameLayoutWidget {
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
        topBar.addChild("back_btn", createLocalActionButton("gui.academy.aerospace_signal_cabin.ops_back") {
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
        val forceArm = createLocalActionButton("gui.academy.aerospace_signal_cabin.ops_force_crash") {
            openForceCrashConfirm()
        }.apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(72f)
                .height(14f)
        }
        forceArmButton = forceArm
        forceActions.addChild("force_crash", forceArm)

        val forceCancel = createActionButton(
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

    private fun detailSectionLabel(labelKey: String): LabelWidget {
        return LabelWidget(Component.translatable(labelKey).string).apply {
            scale = SCALE_SECTION
            alpha = 0.7f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
                .margin(0f, 2f, 0f, 0f)
        }
    }

    private fun detailPickListHost(bindColumn: (LinearLayoutWidget) -> Unit): FrameLayoutWidget {
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

    private fun openOpsDetail(row: SatRow) {
        pendingDetailRow = row
        minecraft.gameMode?.handleInventoryButtonClick(
            menu.containerId,
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

    private fun closeOpsDetail(immediate: Boolean = false) {
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

    private fun openForceCrashConfirm() {
        val overlay = forceConfirmOverlay ?: return
        forceConfirmOpen = true
        AnimationUtil.show(overlay)
    }

    private fun closeForceCrashConfirm(immediate: Boolean = false) {
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

    private fun refreshForceCrashUi() {
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

    private fun refreshSatelliteListUi(force: Boolean = false) {
        val column = satListColumn ?: return
        val blob = blockEntity.managedSatelliteList
        val fingerprint = blob
        if (!force && fingerprint == lastSatListBlob) {
            return
        }
        lastSatListBlob = fingerprint
        column.clearChildren()
        val hasRows = blob.isNotBlank()
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
        blob.lineSequence().forEachIndexed { row, line ->
            if (row >= AerospaceSignalCabinMenu.BUTTON_SELECT_SAT_MAX) {
                return@forEachIndexed
            }
            val parsed = parseSatLine(line) ?: return@forEachIndexed
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
                formatDimPathShort(parsed.dim),
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

    private fun satelliteColumnsRow(
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
    private fun satListColWidths(): SatColWidths {
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

    private fun refreshNetworkListUi(force: Boolean = false) {
        val column = networkListColumn ?: return
        val blob = blockEntity.retargetNetworkList
        if (!force && blob == lastNetworkListBlob) {
            return
        }
        lastNetworkListBlob = blob
        column.clearChildren()
        if (blob.isBlank()) {
            column.addChild("empty", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.ops_no_networks").string).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(16f)
                scale = 0.75f
                alpha = 0.85f
            })
            return
        }
        blob.lineSequence().forEachIndexed { row, line ->
            if (row >= AerospaceSignalCabinMenu.BUTTON_RETARGET_NET_MAX) {
                return@forEachIndexed
            }
            val sep = line.indexOf('|')
            val name = if (sep >= 0) line.substring(0, sep) else line
            val current = sep >= 0 && line.substring(sep + 1) == "1"
            val label = if (current) {
                Component.translatable("gui.academy.aerospace_signal_cabin.ops_network_row_current", name).string
            } else {
                Component.translatable("gui.academy.aerospace_signal_cabin.ops_network_row", name).string
            }
            val button = ButtonWidget()
            button.onClickListener = OnClickListener {
                minecraft.gameMode?.handleInventoryButtonClick(
                    menu.containerId,
                    AerospaceSignalCabinMenu.BUTTON_RETARGET_NET_BASE + row
                )
            }
            button.addChild("back", BlendQuadWidget().apply {
                layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
                alpha = if (current) 0.5f else 0.35f
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
            column.addChild("net_$row", button)
        }
    }

    private fun refreshLaserListUi(force: Boolean = false) {
        val column = laserListColumn ?: return
        val blob = blockEntity.rebindLaserList
        if (!force && blob == lastLaserListBlob) {
            return
        }
        lastLaserListBlob = blob
        column.clearChildren()
        if (blob.isBlank()) {
            column.addChild("empty", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.ops_no_lasers").string).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(16f)
                scale = 0.75f
                alpha = 0.85f
            })
            return
        }
        blob.lineSequence().forEachIndexed { row, line ->
            if (row >= AerospaceSignalCabinMenu.BUTTON_REBIND_LASER_MAX) {
                return@forEachIndexed
            }
            val sep = line.indexOf('|')
            val coords = if (sep >= 0) line.substring(0, sep) else line
            val ready = sep >= 0 && line.substring(sep + 1) == "1"
            val label = if (ready) {
                Component.translatable("gui.academy.aerospace_signal_cabin.ops_laser_row_ready", coords).string
            } else {
                Component.translatable("gui.academy.aerospace_signal_cabin.ops_laser_row_unready", coords).string
            }
            val button = ButtonWidget()
            button.onClickListener = OnClickListener {
                minecraft.gameMode?.handleInventoryButtonClick(
                    menu.containerId,
                    AerospaceSignalCabinMenu.BUTTON_REBIND_LASER_BASE + row
                )
            }
            button.addChild("back", BlendQuadWidget().apply {
                layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
                alpha = if (ready) 0.45f else 0.3f
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
            column.addChild("laser_$row", button)
        }
    }

    private data class SatRow(
        val index: Int,
        val dim: String,
        val phase: String,
        val powered: Boolean,
        val hyper: Boolean,
        val id8: String,
        val laserBound: Boolean,
        val unpoweredTicks: Int,
        val crashTimeout: Int,
        val forceCrashTicks: Int
    )

    private fun parseSatLine(line: String): SatRow? {
        val parts = line.split('|')
        if (parts.size < 6) {
            return null
        }
        val index = parts[0].toIntOrNull() ?: return null
        return SatRow(
            index = index,
            dim = parts[1],
            phase = parts[2],
            powered = parts[3] == "1",
            hyper = parts[4] == "1",
            id8 = parts[5],
            laserBound = parts.size < 7 || parts[6] == "1",
            unpoweredTicks = parts.getOrNull(7)?.toIntOrNull() ?: 0,
            crashTimeout = parts.getOrNull(8)?.toIntOrNull() ?: 6000,
            forceCrashTicks = parts.getOrNull(9)?.toIntOrNull() ?: 0
        )
    }

    private fun selectedSatRow(): SatRow? {
        val selected = blockEntity.selectedSatelliteIndex
        val pending = pendingDetailRow
        val rows = blockEntity.managedSatelliteList.lineSequence().mapNotNull { parseSatLine(it) }.toList()
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

    private fun phaseLabel(phase: String): String {
        return when (phase) {
            "ORBIT" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_phase_orbit").string
            "LAUNCHING" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_phase_launch").string
            "CRASHING" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_phase_crash").string
            else -> phase
        }
    }

    private fun phaseLabelShort(phase: String): String {
        return when (phase) {
            "ORBIT" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_phase_orbit").string
            "LAUNCHING" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_phase_launch").string
            "CRASHING" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_phase_crash").string
            else -> phase
        }
    }

    private fun formatDimPathShort(path: String): String {
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

    private fun formatDimPathFull(path: String): String {
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

    private fun opsCountText(): String {
        val total = blockEntity.managedSatelliteCount
        return if (total <= 0) {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_sat_none").string
        } else {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_sat_count", total).string
        }
    }

    private fun detailTitleText(): String {
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

    private fun detailNetworkText(): String {
        val name = blockEntity.selectedSatNetworkName
        return if (name.isNullOrBlank()) {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_network_unknown").string
        } else {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_network_current", name).string
        }
    }

    private fun detailLaserText(): String {
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

    private fun detailBodyText(): String {
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
            formatDimPathFull(row.dim),
            phaseLabel(row.phase),
            power,
            kind
        ).string
    }

    private fun opsFeedbackText(): String {
        val key = blockEntity.opsFeedbackKey
        return if (key.isNullOrEmpty()) {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_detail_idle").string
        } else {
            Component.translatable(key).string
        }
    }

    private fun createActionButton(labelKey: String, buttonId: Int): ButtonWidget {
        val button = ButtonWidget()
        button.onClickListener = OnClickListener {
            minecraft.gameMode?.handleInventoryButtonClick(menu.containerId, buttonId)
        }
        button.addChild("back", BlendQuadWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            alpha = 0.35f
        })
        button.addChild("label", LabelWidget(Component.translatable(labelKey).string).apply {
            scale = SCALE_BODY
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER)
        })
        return button
    }

    private fun createLocalActionButton(labelKey: String, onClick: () -> Unit): ButtonWidget {
        val button = ButtonWidget()
        button.onClickListener = OnClickListener { onClick() }
        button.addChild("back", BlendQuadWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            alpha = 0.35f
        })
        button.addChild("label", LabelWidget(Component.translatable(labelKey).string).apply {
            scale = SCALE_BODY
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER)
        })
        return button
    }

    companion object {
        private const val SCALE_TITLE = 0.8f
        private const val SCALE_BODY = 0.75f
        private const val SCALE_SECTION = 0.7f
        private const val DETAIL_SPACING = 2f
        private const val LIST_FONT_SIZE = 7f
        private const val COL_SPACING = 3f
        private const val COL_PAD = 2f
        private const val ID_DISPLAY_LEN = 4
        /** 176 face − 6×2 pane margin − 2×2 row inset. */
        private const val LIST_ROW_BUDGET = 156f

        private data class SatColWidths(
            val index: Float,
            val dim: Float,
            val phase: Float,
            val power: Float,
            val kind: Float,
            val id: Float
        )

        /** Share [available] across columns in proportion to each column's content need. */
        private fun allocateByNeed(needs: FloatArray, available: Float): FloatArray {
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

        fun create(
            menu: AerospaceSignalCabinMenu,
            playerInventory: Inventory,
            title: Component,
            mainPos: BlockPos
        ): AerospaceSignalCabinScreen? {
            val entity = Minecraft.getInstance().level?.getBlockEntity(mainPos)
            return if (entity is AerospaceSignalCabinBlockEntity) {
                AerospaceSignalCabinScreen(menu, playerInventory, title, entity)
            } else {
                null
            }
        }
    }
}
