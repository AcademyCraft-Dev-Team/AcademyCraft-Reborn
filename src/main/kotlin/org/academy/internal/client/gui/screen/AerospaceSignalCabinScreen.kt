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
import org.academy.internal.common.world.level.block.entity.AerospaceSignalCabinBlockEntity

class AerospaceSignalCabinScreen private constructor(
    menu: AerospaceSignalCabinMenu,
    playerInventory: Inventory,
    title: Component,
    private val blockEntity: AerospaceSignalCabinBlockEntity
) : ContainerUiScreen<AerospaceSignalCabinMenu>(menu, playerInventory, title) {
    private val mainPos: BlockPos = blockEntity.blockPos
    private var laserLabelSetter: (String) -> Unit = {}
    private var opsCountSetter: (String) -> Unit = {}
    private var detailTitleSetter: (String) -> Unit = {}
    private var detailBodySetter: (String) -> Unit = {}
    private var detailFeedbackSetter: (String) -> Unit = {}
    private var detailNetworkSetter: (String) -> Unit = {}
    private var detailLaserSetter: (String) -> Unit = {}
    private var satListColumn: LinearLayoutWidget? = null
    private var networkListColumn: LinearLayoutWidget? = null
    private var laserListColumn: LinearLayoutWidget? = null
    private var lastSatListBlob: String? = null
    private var lastNetworkListBlob: String? = null
    private var lastLaserListBlob: String? = null
    private var opsListPane: FrameLayoutWidget? = null
    private var opsDetailPane: FrameLayoutWidget? = null
    private var viewingDetail: Boolean = false
    private var pendingDetailRow: SatRow? = null

    override fun onInit(
        pageButtons: RadioGroupWidget,
        invButton: RadioButtonWidget,
        content: FrameLayoutWidget,
        invPage: FrameLayoutWidget
    ) {
        val duration = 600L
        val childDuration = duration - 100

        invPage.addChild("hint", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.hint").string).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(16f)
                .gravity(Gravity.TOP)
                .margin(8f, 4f, 8f, 0f)
            scale = 0.75f
        })

        val laserLabel = LabelWidget(laserSelectionText()).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(14f)
                .gravity(Gravity.TOP)
                .margin(8f, 20f, 8f, 0f)
            scale = 0.75f
        }
        laserLabelSetter = { laserLabel.text = it }
        invPage.addChild("laser_label", laserLabel)

        invPage.addChild("cycle_laser", createActionButton(
            "gui.academy.aerospace_signal_cabin.cycle_laser",
            AerospaceSignalCabinMenu.BUTTON_CYCLE_LASER
        ).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .width(72f)
                .height(18f)
                .gravity(Gravity.TOP_LEFT)
                .margin(8f, 54f, 0f, 0f)
        })

        invPage.addChild("launch", createActionButton(
            "gui.academy.aerospace_signal_cabin.launch",
            AerospaceSignalCabinMenu.BUTTON_LAUNCH
        ).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .width(72f)
                .height(18f)
                .gravity(Gravity.TOP_RIGHT)
                .margin(0f, 54f, 8f, 0f)
        })

        invPage.addChild("cycle_dim", createActionButton(
            "gui.academy.aerospace_signal_cabin.cycle_dim",
            AerospaceSignalCabinMenu.BUTTON_CYCLE_DIM
        ).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .width(96f)
                .height(16f)
                .gravity(Gravity.CENTER_TOP)
                .margin(0f, 74f, 0f, 0f)
        })

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
        pageButtons.startAnimation(
            ObjectAnimator.ofFloat({ pageButtons.alpha = it }, 0f, 1f).setDuration(childDuration)
        )
        pageButtons.startAnimation(
            ObjectAnimator.ofFloat({ pageButtons.translationY = it }, 20f, 0f)
                .setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_CUBIC)
        )
    }

    override fun containerTick() {
        super.containerTick()
        laserLabelSetter(laserSelectionText())
        opsCountSetter(opsCountText())
        refreshSatelliteListUi()
        if (viewingDetail) {
            detailTitleSetter(detailTitleText())
            detailBodySetter(detailBodyText())
            detailLaserSetter(detailLaserText())
            detailNetworkSetter(detailNetworkText())
            detailFeedbackSetter(opsFeedbackText())
            refreshLaserListUi()
            refreshNetworkListUi()
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

        page.addChild("list", listPane)
        page.addChild("detail", detailPane)
        opsListPane = listPane
        opsDetailPane = detailPane
        refreshSatelliteListUi(force = true)
        return page
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
            .margin(8f, 8f, 8f, 8f)

        column.addChild("title", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_title").string).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(16f)
            scale = 0.9f
        })

        column.addChild("list_hint", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_hint").string).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(28f)
            scale = 0.7f
            alpha = 0.9f
        })

        val countLabel = LabelWidget(opsCountText()).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(12f)
            scale = 0.75f
        }
        opsCountSetter = { countLabel.text = it }
        column.addChild("count", countLabel)

        val listScroll = ScrollPanelWidget(Orientation.VERTICAL)
        listScroll.layoutParams = LinearLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(110f)
            .margin(0f, 4f, 0f, 0f)
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

        val column = LinearLayoutWidget()
        column.orientation = Orientation.VERTICAL
        column.spacing = 4f
        column.layoutParams = FrameLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .heightMode(SizeMode.MATCH_PARENT)
            .gravity(Gravity.TOP)
            .margin(8f, 8f, 8f, 8f)

        column.addChild("back_btn", createLocalActionButton("gui.academy.aerospace_signal_cabin.ops_back") {
            closeOpsDetail()
        }.apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(72f)
                .height(16f)
        })

        val title = LabelWidget(detailTitleText()).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(14f)
                .margin(0f, 4f, 0f, 0f)
            scale = 0.85f
        }
        detailTitleSetter = { title.text = it }
        column.addChild("title", title)

        val body = LabelWidget(detailBodyText()).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(24f)
            scale = 0.75f
            alpha = 0.95f
        }
        detailBodySetter = { body.text = it }
        column.addChild("body", body)

        val laserStatus = LabelWidget(detailLaserText()).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(12f)
                .margin(0f, 2f, 0f, 0f)
            scale = 0.75f
        }
        detailLaserSetter = { laserStatus.text = it }
        column.addChild("laser_status", laserStatus)

        column.addChild("laser_hint", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.ops_laser_pick").string).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(12f)
            scale = 0.7f
            alpha = 0.9f
        })

        val laserScroll = ScrollPanelWidget(Orientation.VERTICAL)
        laserScroll.layoutParams = LinearLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(48f)
            .margin(0f, 2f, 0f, 0f)
        val laserColumn = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = 2f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .heightMode(SizeMode.WRAP_CONTENT)
        }
        laserListColumn = laserColumn
        laserScroll.addChild("content", laserColumn)
        column.addChild("laser_list", laserScroll)

        column.addChild("rebind_hint", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.rebind_hint").string).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(16f)
                .margin(0f, 2f, 0f, 0f)
            scale = 0.7f
            alpha = 0.85f
        })

        val networkLabel = LabelWidget(detailNetworkText()).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(12f)
                .margin(0f, 2f, 0f, 0f)
            scale = 0.75f
        }
        detailNetworkSetter = { networkLabel.text = it }
        column.addChild("network_current", networkLabel)

        column.addChild("network_hint", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.ops_network_pick").string).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(14f)
            scale = 0.7f
            alpha = 0.9f
        })

        val networkScroll = ScrollPanelWidget(Orientation.VERTICAL)
        networkScroll.layoutParams = LinearLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(48f)
            .margin(0f, 2f, 0f, 0f)
        val networkColumn = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = 2f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .heightMode(SizeMode.WRAP_CONTENT)
        }
        networkListColumn = networkColumn
        networkScroll.addChild("content", networkColumn)
        column.addChild("network_list", networkScroll)

        column.addChild("retarget_hint", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.retarget_hint").string).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(28f)
                .margin(0f, 4f, 0f, 0f)
            scale = 0.7f
            alpha = 0.85f
        })

        val feedback = LabelWidget(opsFeedbackText()).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(24f)
                .margin(0f, 2f, 0f, 0f)
            scale = 0.75f
        }
        detailFeedbackSetter = { feedback.text = it }
        column.addChild("feedback", feedback)

        pane.addChild("column", column)
        return pane
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
        refreshLaserListUi(force = true)
        refreshNetworkListUi(force = true)
    }

    private fun closeOpsDetail(immediate: Boolean = false) {
        pendingDetailRow = null
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

    private fun refreshSatelliteListUi(force: Boolean = false) {
        val column = satListColumn ?: return
        val blob = blockEntity.managedSatelliteList
        val fingerprint = blob
        if (!force && fingerprint == lastSatListBlob) {
            return
        }
        lastSatListBlob = fingerprint
        column.clearChildren()
        if (blob.isBlank()) {
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
            val label = Component.translatable(
                "gui.academy.aerospace_signal_cabin.ops_sat_row",
                parsed.index,
                parsed.dim,
                phaseLabel(parsed.phase),
                if (parsed.powered) "ON" else "OFF",
                if (parsed.hyper) "H" else "N",
                parsed.id8
            ).string
            val button = ButtonWidget()
            button.onClickListener = OnClickListener { openOpsDetail(parsed) }
            button.addChild("back", BlendQuadWidget().apply {
                layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
                alpha = 0.35f
            })
            button.addChild("label", LabelWidget(label).apply {
                layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                    .gravity(Gravity.CENTER_LEFT)
                    .margin(4f, 0f, 4f, 0f)
                scale = 0.7f
            })
            button.layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(18f)
            column.addChild("sat_${parsed.index}", button)
        }
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
                scale = 0.7f
            })
            button.layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(16f)
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
                scale = 0.7f
            })
            button.layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(16f)
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
        val crashTimeout: Int
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
            crashTimeout = parts.getOrNull(8)?.toIntOrNull() ?: 6000
        )
    }

    private fun selectedSatRow(): SatRow? {
        val selected = blockEntity.selectedSatelliteIndex
        val fromServer = blockEntity.managedSatelliteList.lineSequence()
            .mapNotNull { parseSatLine(it) }
            .firstOrNull { it.index == selected }
        val pending = pendingDetailRow
        if (fromServer != null) {
            if (pending != null && pending.index == fromServer.index) {
                pendingDetailRow = null
            }
            return fromServer
        }
        return pending
    }

    private fun phaseLabel(phase: String): String {
        return when (phase) {
            "ORBIT" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_phase_orbit").string
            "LAUNCHING" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_phase_launch").string
            "CRASHING" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_phase_crash").string
            else -> phase
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
            row.dim,
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
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER)
        })
        return button
    }

    companion object {
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
