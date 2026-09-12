package org.academy.internal.client.gui.screen

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerLevelAccess
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.event.OnClickListener
import org.academy.api.client.gui.layout.Gravity
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

class SatelliteLaunchPadScreen private constructor(
    menu: SatelliteLaunchPadMenu,
    playerInventory: Inventory,
    title: Component,
    private val blockEntity: SatelliteLaunchPadBlockEntity
) : ContainerUiScreen<SatelliteLaunchPadMenu>(menu, playerInventory, title) {
    private val mainPos: BlockPos = blockEntity.blockPos
    private var laserLabelSetter: (String) -> Unit = {}
    private var dimLabelSetter: (String) -> Unit = {}
    private var launchFeedbackSetter: (String) -> Unit = {}
    private lateinit var energyValueLabel: LabelWidget

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
            "gui.academy.satellite_launch_pad.cycle_laser",
            SatelliteLaunchPadMenu.BUTTON_CYCLE_LASER
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
            "gui.academy.satellite_launch_pad.launch",
            SatelliteLaunchPadMenu.BUTTON_LAUNCH
        ).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .width(72f)
                .height(16f)
                .gravity(Gravity.TOP_RIGHT)
                .margin(0f, 54f, 8f, 0f)
        }
        invPage.addChild("launch", launchBtn)
        playOpenReveal(launchBtn, 1f, duration, childDuration)

        val cycleDimBtn = createActionButton(
            "gui.academy.satellite_launch_pad.cycle_dim",
            SatelliteLaunchPadMenu.BUTTON_CYCLE_DIM
        ).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .width(96f)
                .height(14f)
                .gravity(Gravity.CENTER_TOP)
                .margin(0f, 72f, 0f, 0f)
        }
        invPage.addChild("cycle_dim", cycleDimBtn)
        playOpenReveal(cycleDimBtn, 1f, duration, childDuration)

        val wirelessPage = WirelessPanelUtil.create(mainPos, true)
        wirelessPage.visibility = Widget.Visibility.GONE
        wirelessPage.isEnabled = false
        content.addChild("page_wireless", wirelessPage)

        val wirelessButton = createButton(R.textures.gui.icon.icon_wireless)
        wirelessButton.layoutParams = WidgetContainer.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(16f)
        pageButtons.addChild("wireless", wirelessButton)

        pageButtons.onSelectionChanged = {
            when (it.name) {
                "inv" -> {
                    AnimationUtil.hide(wirelessPage)
                    AnimationUtil.show(invPage)
                    isHandleContainer = true
                    isRenderInventory = true
                }
                "wireless" -> {
                    AnimationUtil.hide(invPage)
                    AnimationUtil.show(wirelessPage)
                    isHandleContainer = false
                    isRenderInventory = false
                }
            }
        }
        pageButtons.selectButton(invButton)
        playOpenReveal(pageButtons, 1f, duration, childDuration)

        val info = InfoAreaUtil.create(this, (leftPos + imageWidth).toFloat(), (topPos - 22).toFloat())
        run {
            val p = WidgetContainer.LayoutParams().gravity(Gravity.CENTER_RIGHT)
            energyValueLabel = LabelWidget("0 AF")
            energyValueLabel.layoutParams = p
            val energyLayout = InfoAreaUtil.createInfoRow("ENERGY", "icon_energy", -0xda3b01, energyValueLabel)
            info.addChild("energy_layout", energyLayout)
        }
    }

    private fun playOpenReveal(
        widget: Widget,
        targetAlpha: Float,
        duration: Long,
        childDuration: Long
    ) {
        AnimationUtil.reveal(
            widget = widget,
            targetAlpha = targetAlpha,
            alphaDuration = childDuration,
            translationDuration = duration,
            yInterpolator = EasingFunctions.EASE_OUT_CUBIC,
            applyShowFlags = false
        )
    }

    override fun containerTick() {
        super.containerTick()
        laserLabelSetter(laserSelectionText())
        dimLabelSetter(launchDimText())
        launchFeedbackSetter(launchFeedbackText())
        if (::energyValueLabel.isInitialized) {
            energyValueLabel.text = "${blockEntity.energyStored} / ${blockEntity.maxEnergyStorage} AF"
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

    private fun laserSelectionText(): String {
        if (blockEntity.connectedNodePosition == null) {
            return Component.translatable("gui.academy.satellite_launch_pad.laser_need_wireless").string
        }
        val pos = blockEntity.selectedLaserPos
        if (pos == null) {
            return Component.translatable(
                "gui.academy.satellite_launch_pad.laser_none",
                blockEntity.selectableLaserCount
            ).string
        }
        val tower = minecraft.level?.getBlockEntity(pos)
            as? org.academy.internal.common.world.level.block.entity.EnergyLaserTowerBlockEntity
        val key = when {
            tower == null -> "gui.academy.satellite_launch_pad.laser_selected_unready"
            !tower.hasClearSky() -> "gui.academy.satellite_launch_pad.laser_selected_blocked"
            tower.energyStored <= 0 -> "gui.academy.satellite_launch_pad.laser_selected_nopower"
            else -> "gui.academy.satellite_launch_pad.laser_selected_ready"
        }
        return Component.translatable(key, pos.x, pos.y, pos.z).string
    }

    private fun launchFeedbackText(): String {
        val key = blockEntity.launchFeedbackKey
        return if (key.isBlank()) "" else Component.translatable(key).string
    }

    companion object {
        private const val SCALE_BODY = 0.75f

        fun create(
            menu: SatelliteLaunchPadMenu,
            playerInventory: Inventory,
            title: Component,
            mainPos: BlockPos
        ): SatelliteLaunchPadScreen? {
            val minecraft = Minecraft.getInstance()
            val level = minecraft.level ?: return null
            val player = minecraft.player ?: return null
            val entity = level.getBlockEntity(mainPos)
            return if (entity is SatelliteLaunchPadBlockEntity) {
                val boundMenu = SatelliteLaunchPadMenu(
                    menu.containerId,
                    playerInventory,
                    ContainerLevelAccess.create(level, mainPos),
                    entity
                )
                player.containerMenu = boundMenu
                SatelliteLaunchPadScreen(boundMenu, playerInventory, title, entity)
            } else {
                null
            }
        }
    }
}
