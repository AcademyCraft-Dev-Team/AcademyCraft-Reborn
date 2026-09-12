package org.academy.internal.client.gui.screen

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.screen.ContainerUiScreen
import org.academy.api.client.gui.util.InfoAreaUtil
import org.academy.api.client.gui.util.WirelessPanelUtil
import org.academy.api.client.gui.widget.*
import org.academy.api.client.resources.R
import org.academy.api.client.util.AnimationUtil
import org.academy.internal.common.world.inventory.SatelliteLaunchPadMenu
import org.academy.internal.common.world.item.NetworkRelaySatelliteItem
import org.academy.internal.common.world.level.block.entity.SatelliteLaunchPadBlockEntity
import org.academy.internal.server.misaka.MisakaNetworkLasers.LaserRow

class SatelliteLaunchPadScreen private constructor(
    menu: SatelliteLaunchPadMenu,
    playerInventory: Inventory,
    title: Component,
    private val blockEntity: SatelliteLaunchPadBlockEntity
) : ContainerUiScreen<SatelliteLaunchPadMenu>(menu, playerInventory, title) {
    private val mainPos: BlockPos = blockEntity.blockPos
    private var dimLabelSetter: (String) -> Unit = {}
    private var launchFeedbackSetter: (String) -> Unit = {}
    private lateinit var energyValueLabel: LabelWidget
    private var laserListColumn: LinearLayoutWidget? = null
    private var lastLaserList: List<LaserRow>? = null
    private var assetPage: DeviceAssetPage? = null

    override fun onInit(
        pageButtons: RadioGroupWidget,
        invButton: RadioButtonWidget,
        content: FrameLayoutWidget,
        invPage: FrameLayoutWidget
    ) {
        val duration = 600L
        val childDuration = duration - 100

        val hint = LabelWidget(Component.translatable("gui.academy.satellite_launch_pad.hint").string).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(24f)
                .gravity(Gravity.TOP)
                .margin(8f, 2f, 8f, 0f)
            scale = 0.75f
        }
        invPage.addChild("hint", hint)
        MisakaMachineUi.playOpenReveal(hint, 1f, duration, childDuration)

        val launchPage = createLaunchPage()
        launchPage.visibility = Widget.Visibility.GONE
        launchPage.isEnabled = false
        content.addChild("page_launch", launchPage)

        val wirelessPage = WirelessPanelUtil.create(mainPos, true)
        wirelessPage.visibility = Widget.Visibility.GONE
        wirelessPage.isEnabled = false
        content.addChild("page_wireless", wirelessPage)

        val launchButton = createButton(R.textures.gui.icon.icon_teleporter)
        MisakaMachineUi.sizeRailButton(launchButton)
        pageButtons.addChild("launch", launchButton)

        val wirelessButton = createButton(R.textures.gui.icon.icon_wireless)
        MisakaMachineUi.sizeRailButton(wirelessButton)
        pageButtons.addChild("wireless", wirelessButton)

        val asset = DeviceAssetPage.attach(
            pageButtons = pageButtons,
            invButton = invButton,
            content = content,
            devicePos = mainPos,
            isOwner = { menu.viewerIsOwner() },
            createRailButton = { createButton(R.textures.gui.icon.icon_settings) }
        )
        assetPage = asset

        pageButtons.onSelectionChanged = {
            when (it.name) {
                "inv" -> {
                    AnimationUtil.hide(launchPage)
                    AnimationUtil.hide(wirelessPage)
                    asset.hide()
                    AnimationUtil.show(invPage)
                    isHandleContainer = true
                    isRenderInventory = true
                }
                "launch" -> {
                    AnimationUtil.hide(invPage)
                    AnimationUtil.hide(wirelessPage)
                    asset.hide()
                    refreshLaserListUi(force = true)
                    AnimationUtil.show(launchPage)
                    isHandleContainer = false
                    isRenderInventory = false
                }
                "wireless" -> {
                    AnimationUtil.hide(invPage)
                    AnimationUtil.hide(launchPage)
                    asset.hide()
                    AnimationUtil.show(wirelessPage)
                    isHandleContainer = false
                    isRenderInventory = false
                }
                "asset" -> {
                    AnimationUtil.hide(invPage)
                    AnimationUtil.hide(launchPage)
                    AnimationUtil.hide(wirelessPage)
                    asset.show()
                    isHandleContainer = false
                    isRenderInventory = false
                }
            }
        }
        pageButtons.selectButton(invButton)
        MisakaMachineUi.playOpenReveal(pageButtons, 1f, duration, childDuration)

        val info = InfoAreaUtil.create(this, (leftPos + imageWidth).toFloat(), (topPos - 22).toFloat())
        run {
            val p = WidgetContainer.LayoutParams().gravity(Gravity.CENTER_RIGHT)
            energyValueLabel = LabelWidget("0 AF")
            energyValueLabel.layoutParams = p
            val energyLayout = InfoAreaUtil.createInfoRow("ENERGY", "icon_energy", -0xda3b01, energyValueLabel)
            info.addChild("energy_layout", energyLayout)
        }
    }

    private fun createLaunchPage(): FrameLayoutWidget {
        val pane = FrameLayoutWidget()
        pane.layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        pane.addChild("back", BlendQuadWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            alpha = 0.5f
        })

        val column = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = 2f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.TOP)
                .margin(6f, 6f, 6f, 6f)
        }

        column.addChild("title", LabelWidget(
            Component.translatable("gui.academy.satellite_launch_pad.ops_title").string
        ).apply {
            scale = 0.8f
            alpha = 0.95f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(12f)
        })

        val dimLabel = LabelWidget(launchDimText()).apply {
            scale = MisakaMachineUi.SCALE_BODY
            alpha = 0.88f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(12f)
        }
        dimLabelSetter = { dimLabel.text = it }
        column.addChild("dim", dimLabel)

        val actions = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = 4f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(16f)
        }
        actions.addChild(
            "cycle_dim",
            MisakaMachineUi.menuActionButton(
                menu,
                "gui.academy.satellite_launch_pad.cycle_dim",
                SatelliteLaunchPadMenu.BUTTON_CYCLE_DIM
            ).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .height(16f)
            }
        )
        actions.addChild(
            "launch",
            MisakaMachineUi.menuActionButton(
                menu,
                "gui.academy.satellite_launch_pad.launch",
                SatelliteLaunchPadMenu.BUTTON_LAUNCH
            ).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .height(16f)
            }
        )
        column.addChild("actions", actions)

        val feedback = LabelWidget(launchFeedbackText()).apply {
            scale = 0.7f
            alpha = 0.78f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
        }
        launchFeedbackSetter = { feedback.text = it }
        column.addChild("feedback", feedback)

        column.addChild("laser_section", LabelWidget(
            Component.translatable("gui.academy.satellite_launch_pad.laser_pick").string
        ).apply {
            scale = 0.7f
            alpha = 0.7f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
                .margin(0f, 2f, 0f, 0f)
        })

        val listHost = FrameLayoutWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .height(0f)
        }
        listHost.addChild("frame", BlendQuadWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            alpha = 0.22f
        })
        val scroll = ScrollPanelWidget(Orientation.VERTICAL).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .margin(2f, 2f, 2f, 2f)
        }
        val rows = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = 1f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .heightMode(SizeMode.WRAP_CONTENT)
        }
        laserListColumn = rows
        scroll.setContent(rows)
        listHost.addChild("scroll", scroll)
        column.addChild("lasers", listHost)

        pane.addChild("column", column)
        refreshLaserListUi(force = true)
        return pane
    }

    override fun containerTick() {
        super.containerTick()
        assetPage?.tick(menu.viewerIsOwner())
        dimLabelSetter(launchDimText())
        launchFeedbackSetter(launchFeedbackText())
        refreshLaserListUi()
        if (::energyValueLabel.isInitialized) {
            energyValueLabel.text = "${blockEntity.energyStored} / ${blockEntity.maxEnergyStorage} AF"
        }
    }

    private fun refreshLaserListUi(force: Boolean = false) {
        val column = laserListColumn ?: return
        val rows = blockEntity.laserPickList
        if (!force && rows == lastLaserList) {
            return
        }
        lastLaserList = rows
        column.clearChildren()
        if (rows.isEmpty()) {
            val emptyKey = if (blockEntity.connectedNodePosition == null) {
                "gui.academy.satellite_launch_pad.laser_need_wireless"
            } else {
                "gui.academy.satellite_launch_pad.no_lasers"
            }
            column.addChild("empty", LabelWidget(Component.translatable(emptyKey).string).apply {
                scale = 0.75f
                alpha = 0.85f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(16f)
            })
            return
        }
        val selected = blockEntity.selectedLaserPos
        rows.forEachIndexed { index, entry ->
            if (index >= SatelliteLaunchPadMenu.BUTTON_SELECT_LASER_MAX) {
                return@forEachIndexed
            }
            val pos = entry.pos
            val coords = "${pos.x},${pos.y},${pos.z}"
            val current = selected != null && selected == pos
            val label = when {
                current && entry.ready -> Component.translatable(
                    "gui.academy.satellite_launch_pad.laser_row_current_ready",
                    coords
                ).string
                current -> Component.translatable(
                    "gui.academy.satellite_launch_pad.laser_row_current_unready",
                    coords
                ).string
                entry.ready -> Component.translatable(
                    "gui.academy.satellite_launch_pad.laser_row_ready",
                    coords
                ).string
                else -> Component.translatable(
                    "gui.academy.satellite_launch_pad.laser_row_unready",
                    coords
                ).string
            }
            val button = MisakaMachineUi.menuListSelectButton(
                menu = menu,
                label = label,
                buttonId = SatelliteLaunchPadMenu.BUTTON_SELECT_LASER_BASE + index,
                enabled = !current,
                selected = current
            )
            button.alpha = if (current) 0.7f else if (entry.ready) 1f else 0.55f
            column.addChild("laser_$index", button)
        }
    }

    private fun launchDimText(): String {
        val stack = menu.getSlot(0).item
        if (!NetworkRelaySatelliteItem.isSatellite(stack)) {
            return Component.translatable("gui.academy.satellite_launch_pad.dim_idle").string
        }
        if (!NetworkRelaySatelliteItem.isHyper(stack)) {
            return Component.translatable("gui.academy.satellite_launch_pad.dim_overworld_only").string
        }
        val path = NetworkRelaySatelliteItem.targetDimension(stack).identifier().path
        return Component.translatable(
            "gui.academy.satellite_launch_pad.dim_target",
            AerospaceCabinOpsUi.formatDimPathFull(path)
        ).string
    }

    private fun launchFeedbackText(): String {
        val key = blockEntity.launchFeedbackKey
        return if (key.isBlank()) "" else Component.translatable(key).string
    }

    companion object {
        fun create(
            menu: SatelliteLaunchPadMenu,
            playerInventory: Inventory,
            title: Component,
            mainPos: BlockPos
        ): SatelliteLaunchPadScreen? {
            val entity = Minecraft.getInstance().level?.getBlockEntity(mainPos)
            // Keep the factory menu so server-synced owner ContainerData is not discarded.
            return if (entity is SatelliteLaunchPadBlockEntity) {
                SatelliteLaunchPadScreen(menu, playerInventory, title, entity)
            } else {
                null
            }
        }
    }
}
