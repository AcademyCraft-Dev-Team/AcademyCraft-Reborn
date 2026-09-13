package org.academy.internal.client.gui.screen
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerLevelAccess
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
    private var waterLabelSetter: (String) -> Unit = {}
    private var waterWarnSetter: (String) -> Unit = {}
    private var waterWarnAlphaSetter: (Float) -> Unit = {}
    private var launchFeedbackSetter: (String) -> Unit = {}
    private lateinit var energyValueLabel: LabelWidget
    private var laserListColumn: LinearLayoutWidget? = null
    private var lastLaserList: List<LaserRow>? = null
    private var assetPage: DeviceAssetPage? = null
    private var forceConfirmOverlay: FrameLayoutWidget? = null
    private var forceConfirmOpen: Boolean = false
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
                .height(28f)
                .gravity(Gravity.TOP)
                .margin(8f, 2f, 8f, 0f)
            scale = 0.7f
        }
        invPage.addChild("hint", hint)
        MisakaMachineUi.playOpenReveal(hint, 1f, duration, childDuration)
        // Temporary: no machine face art yet — draw slot wells so pad slots are visible.
        addPadSlotGuides(invPage)
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
            isOwner = { viewerOwnsPad() },
            createRailButton = { createButton(R.textures.gui.icon.icon_settings) }
        )
        assetPage = asset
        pageButtons.onSelectionChanged = {
            when (it.name) {
                "inv" -> {
                    closeForceConfirm(immediate = true)
                    AnimationUtil.hide(launchPage)
                    AnimationUtil.hide(wirelessPage)
                    asset.hide()
                    // Prefer direct restore over AnimationUtil.show: show() cancelAnimations()
                    // would kill ContainerUiScreen's open height anim on a re-select path.
                    invPage.cancelAnimations()
                    invPage.visibility = Widget.Visibility.VISIBLE
                    invPage.isEnabled = true
                    invPage.alpha = 1f
                    invPage.translationY = 0f
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
                    closeForceConfirm(immediate = true)
                    AnimationUtil.hide(invPage)
                    AnimationUtil.hide(launchPage)
                    asset.hide()
                    AnimationUtil.show(wirelessPage)
                    isHandleContainer = false
                    isRenderInventory = false
                }
                "asset" -> {
                    closeForceConfirm(immediate = true)
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
        // Ensure inventory slots render on the first frame (no deferred tab restore needed).
        invPage.translationY = 0f
        invPage.alpha = 1f
        isHandleContainer = true
        isRenderInventory = true
        val info = InfoAreaUtil.create(this, (leftPos + imageWidth).toFloat(), (topPos - 22).toFloat())
        run {
            val p = WidgetContainer.LayoutParams().gravity(Gravity.CENTER_RIGHT)
            energyValueLabel = LabelWidget("0 AF")
            energyValueLabel.layoutParams = p
            val energyLayout = InfoAreaUtil.createInfoRow("ENERGY", "icon_energy", -0xda3b01, energyValueLabel)
            info.addChild("energy_layout", energyLayout)
        }
    }
    /**
     * Placeholder slot chrome until a dedicated launch-pad face texture exists.
     *
     * [ContainerUiScreen] places the inv page at `topPos - 22`, while vanilla slots
     * render at `topPos + slot.y`. Guides must add that 22px so wells sit on the items.
     */
    private fun addPadSlotGuides(invPage: FrameLayoutWidget) {
        val labels = listOf(
            "gui.academy.satellite_launch_pad.slot_satellite",
            "gui.academy.satellite_launch_pad.slot_obsidian",
            "gui.academy.satellite_launch_pad.slot_tnt"
        )
        for (index in 0 until SatelliteLaunchPadMenu.PAD_SLOT_COUNT) {
            val slot = menu.getSlot(index)
            val pageX = slot.x.toFloat()
            val pageY = slot.y + INV_PAGE_SLOT_Y_OFFSET
            val well = FrameLayoutWidget().apply {
                layoutParams = FrameLayoutWidget.LayoutParams()
                    .width(18f)
                    .height(18f)
                    .gravity(Gravity.TOP_LEFT)
                    .margin(pageX - 1f, pageY - 1f, 0f, 0f)
            }
            // Light outer rim.
            well.addChild("rim", FillWidget(0xD9FFFFFF.toInt()).apply {
                layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
                alpha = 0.85f
            })
            // Dark recessed well (matches vanilla 16×16 hit box).
            well.addChild("recess", FillWidget(0xE6000000.toInt()).apply {
                layoutParams = FrameLayoutWidget.LayoutParams()
                    .width(16f)
                    .height(16f)
                    .gravity(Gravity.CENTER)
                alpha = 0.72f
            })
            invPage.addChild("pad_slot_$index", well)

            invPage.addChild(
                "pad_slot_label_$index",
                LabelWidget(Component.translatable(labels[index]).string).apply {
                    layoutParams = FrameLayoutWidget.LayoutParams()
                        .width(62f)
                        .height(16f)
                        .gravity(Gravity.TOP_LEFT)
                        .margin(pageX - 68f, pageY, 0f, 0f)
                    scale = 0.65f
                    alpha = 0.9f
                }
            )
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
        val waterLabel = LabelWidget(waterStatusText()).apply {
            scale = MisakaMachineUi.SCALE_BODY
            alpha = 0.88f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(12f)
        }
        waterLabelSetter = { waterLabel.text = it }
        column.addChild("water", waterLabel)
        val waterWarn = LabelWidget(waterWarnText()).apply {
            scale = 0.7f
            alpha = if (blockEntity.hasEnoughLaunchWater()) 0f else 0.95f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(18f)
            setRed(1f)
            setGreen(0.45f)
            setBlue(0.4f)
        }
        waterWarnSetter = { waterWarn.text = it }
        waterWarnAlphaSetter = { waterWarn.alpha = it }
        column.addChild("water_warn", waterWarn)
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
            MisakaMachineUi.localActionButton("gui.academy.satellite_launch_pad.launch") {
                onLaunchClicked()
            }.apply {
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
        val confirm = createForceConfirmOverlay()
        confirm.visibility = Widget.Visibility.GONE
        confirm.isEnabled = false
        forceConfirmOverlay = confirm
        pane.addChild("force_confirm", confirm)
        refreshLaserListUi(force = true)
        return pane
    }
    private fun createForceConfirmOverlay(): FrameLayoutWidget {
        val overlay = FrameLayoutWidget()
        overlay.layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        overlay.addChild("dim", BlendQuadWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            alpha = 0.72f
        })
        val panel = FrameLayoutWidget()
        panel.layoutParams = FrameLayoutWidget.LayoutParams()
            .width(168f)
            .height(108f)
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
        column.addChild(
            "title",
            LabelWidget(Component.translatable("gui.academy.satellite_launch_pad.force_confirm_title").string).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(14f)
                scale = 0.85f
            }
        )
        column.addChild(
            "body",
            LabelWidget(Component.translatable("gui.academy.satellite_launch_pad.force_confirm_body").string).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(42f)
                scale = 0.7f
                alpha = 0.92f
            }
        )
        val actions = LinearLayoutWidget()
        actions.orientation = Orientation.HORIZONTAL
        actions.spacing = 8f
        actions.layoutParams = LinearLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(18f)
            .margin(0f, 4f, 0f, 0f)
        actions.addChild(
            "dismiss",
            MisakaMachineUi.localActionButton("gui.academy.satellite_launch_pad.force_confirm_no") {
                closeForceConfirm()
            }.apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(18f)
            }
        )
        actions.addChild(
            "confirm",
            MisakaMachineUi.localActionButton("gui.academy.satellite_launch_pad.force_confirm_yes") {
                closeForceConfirm()
                sendLaunch()
            }.apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(18f)
            }
        )
        column.addChild("actions", actions)
        panel.addChild("column", column)
        overlay.addChild("panel", panel)
        return overlay
    }
    private fun onLaunchClicked() {
        if (blockEntity.hasEnoughLaunchWater()) {
            sendLaunch()
        } else {
            openForceConfirm()
        }
    }
    private fun sendLaunch() {
        Minecraft.getInstance().gameMode?.handleInventoryButtonClick(
            menu.containerId,
            SatelliteLaunchPadMenu.BUTTON_LAUNCH
        )
    }
    private fun openForceConfirm() {
        val overlay = forceConfirmOverlay ?: return
        forceConfirmOpen = true
        overlay.cancelAnimations()
        overlay.visibility = Widget.Visibility.VISIBLE
        overlay.isEnabled = true
        overlay.alpha = 1f
        overlay.translationY = 0f
    }
    private fun closeForceConfirm(immediate: Boolean = false) {
        val overlay = forceConfirmOverlay ?: return
        forceConfirmOpen = false
        overlay.cancelAnimations()
        overlay.visibility = Widget.Visibility.GONE
        overlay.isEnabled = false
        overlay.alpha = 0f
        overlay.translationY = 0f
    }
    override fun containerTick() {
        super.containerTick()
        assetPage?.tick(viewerOwnsPad())
        dimLabelSetter(launchDimText())
        waterLabelSetter(waterStatusText())
        waterWarnSetter(waterWarnText())
        waterWarnAlphaSetter(if (blockEntity.hasEnoughLaunchWater()) 0f else 0.95f)
        launchFeedbackSetter(launchFeedbackText())
        refreshLaserListUi()
        if (::energyValueLabel.isInitialized) {
            energyValueLabel.text = "${blockEntity.energyStored} / ${blockEntity.maxEnergyStorage} AF"
        }
    }

    private fun viewerOwnsPad(): Boolean {
        val player = Minecraft.getInstance().player
        return menu.viewerIsOwner() || (player != null && blockEntity.isOwner(player))
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
        val stack = menu.getSlot(SatelliteLaunchPadMenu.SLOT_SATELLITE).item
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
    private fun waterStatusText(): String {
        val buckets = blockEntity.waterMb / SatelliteLaunchPadBlockEntity.WATER_BUCKET_MB
        val capacityBuckets =
            SatelliteLaunchPadBlockEntity.WATER_CAPACITY_MB / SatelliteLaunchPadBlockEntity.WATER_BUCKET_MB
        return Component.translatable(
            "gui.academy.satellite_launch_pad.water_status",
            buckets,
            capacityBuckets,
            blockEntity.waterMb,
            SatelliteLaunchPadBlockEntity.WATER_CAPACITY_MB
        ).string
    }
    private fun waterWarnText(): String {
        return if (blockEntity.hasEnoughLaunchWater()) {
            ""
        } else {
            Component.translatable("gui.academy.satellite_launch_pad.water_low_warn").string
        }
    }
    private fun launchFeedbackText(): String {
        val key = blockEntity.launchFeedbackKey
        return if (key.isBlank()) "" else Component.translatable(key).string
    }
    companion object {
        /**
         * [ContainerUiScreen] hosts inv widgets at `topPos - 22`; slots render at `topPos + y`.
         */
        private const val INV_PAGE_SLOT_Y_OFFSET = 22f

        fun create(
            menu: SatelliteLaunchPadMenu,
            playerInventory: Inventory,
            title: Component,
            mainPos: BlockPos
        ): SatelliteLaunchPadScreen? {
            val level = Minecraft.getInstance().level ?: return null
            val entity = level.getBlockEntity(mainPos)
            if (entity !is SatelliteLaunchPadBlockEntity) {
                return null
            }
            // Factory OpenScreen menus use an empty SimpleContainer; bind the live client BE so
            // pad slots show immediately (and stay correct if set-content raced the open packet).
            val boundMenu = if (menu.blockEntity === entity) {
                menu
            } else {
                SatelliteLaunchPadMenu(
                    menu.containerId,
                    playerInventory,
                    ContainerLevelAccess.create(level, mainPos),
                    entity
                )
            }
            return SatelliteLaunchPadScreen(boundMenu, playerInventory, title, entity)
        }
    }
}
