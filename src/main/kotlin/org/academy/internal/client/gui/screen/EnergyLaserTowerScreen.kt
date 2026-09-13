package org.academy.internal.client.gui.screen

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.screen.ContainerUiScreen
import org.academy.api.client.gui.util.InfoAreaUtil
import org.academy.api.client.gui.util.WirelessPanelUtil
import org.academy.api.client.gui.widget.*
import org.academy.api.client.resources.R
import org.academy.api.client.util.AnimationUtil
import org.academy.internal.common.world.inventory.EnergyLaserTowerMenu
import org.academy.internal.common.world.level.block.entity.EnergyLaserTowerBlockEntity

class EnergyLaserTowerScreen private constructor(
    menu: EnergyLaserTowerMenu,
    playerInventory: Inventory,
    title: Component,
    private val blockEntity: EnergyLaserTowerBlockEntity
) : ContainerUiScreen<EnergyLaserTowerMenu>(menu, playerInventory, title) {
    private val mainPos: BlockPos = blockEntity.blockPos
    private lateinit var energyValueLabel: LabelWidget
    private lateinit var statusValueLabel: LabelWidget
    private lateinit var feedValueLabel: LabelWidget
    private var assetPage: DeviceAssetPage? = null

    override fun onInit(
        pageButtons: RadioGroupWidget,
        invButton: RadioButtonWidget,
        content: FrameLayoutWidget,
        invPage: FrameLayoutWidget
    ) {
        val duration = 600L
        val childDuration = duration - 100

        invPage.addChild("hint", LabelWidget(Component.translatable("gui.academy.energy_laser_tower.hint").string).apply {
            scale = 0.75f
            alpha = 0.78f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .heightMode(SizeMode.WRAP_CONTENT)
                .gravity(Gravity.TOP)
                .margin(8f, 8f, 8f, 0f)
        })

        val wirelessPage = WirelessPanelUtil.create(mainPos, true)
        wirelessPage.visibility = Widget.Visibility.GONE
        wirelessPage.isEnabled = false
        content.addChild("page_wireless", wirelessPage)

        val wirelessButton = createButton(R.textures.gui.icon.icon_wireless)
        MisakaMachineUi.sizeRailButton(wirelessButton)
        pageButtons.addChild("wireless", wirelessButton)

        val asset = DeviceAssetPage.attach(
            pageButtons = pageButtons,
            invButton = invButton,
            content = content,
            devicePos = mainPos,
            isOwner = { viewerOwnsTower() },
            createRailButton = { createButton(R.textures.gui.icon.icon_settings) }
        )
        assetPage = asset

        pageButtons.onSelectionChanged = {
            when (it.name) {
                "inv" -> {
                    AnimationUtil.hide(wirelessPage)
                    asset.hide()
                    AnimationUtil.show(invPage)
                    isHandleContainer = true
                    isRenderInventory = true
                }
                "wireless" -> {
                    AnimationUtil.hide(invPage)
                    asset.hide()
                    AnimationUtil.show(wirelessPage)
                    isHandleContainer = false
                    isRenderInventory = false
                }
                "asset" -> {
                    AnimationUtil.hide(invPage)
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
            val p = WidgetContainer.LayoutParams()
                .gravity(Gravity.CENTER_RIGHT)
            energyValueLabel = LabelWidget("0 AF")
            energyValueLabel.layoutParams = p
            val energyLayout = InfoAreaUtil.createInfoRow("ENERGY", "icon_energy", -0xda3b01, energyValueLabel)
            info.addChild("energy_layout", energyLayout)

            val infoLabel = LabelWidget("Information")
            infoLabel.layoutParams = LinearLayoutWidget.LayoutParams()
                .padding(6.5f, 0f, 0f, 0f)
            infoLabel.scale = 0.75f
            info.addChild("label_info", infoLabel)

            statusValueLabel = LabelWidget("")
            statusValueLabel.layoutParams = WidgetContainer.LayoutParams()
                .gravity(Gravity.CENTER_RIGHT)
                .sizeMode(SizeMode.WRAP_CONTENT)
            val statusLayout = InfoAreaUtil.createAttributeRow("Status", statusValueLabel)
            info.addChild("status_layout", statusLayout)

            feedValueLabel = LabelWidget("")
            feedValueLabel.layoutParams = WidgetContainer.LayoutParams()
                .gravity(Gravity.CENTER_RIGHT)
                .sizeMode(SizeMode.WRAP_CONTENT)
            val feedLayout = InfoAreaUtil.createAttributeRow("Feed", feedValueLabel)
            info.addChild("feed_layout", feedLayout)
        }
    }

    override fun containerTick() {
        super.containerTick()
        assetPage?.tick(viewerOwnsTower())
        energyValueLabel.text = "${blockEntity.energyStored} / ${blockEntity.maxEnergyStorage} AF"
        statusValueLabel.text = when {
            !blockEntity.hasClearSky() -> Component.translatable("gui.academy.energy_laser_tower.status_blocked").string
            blockEntity.connectedNodePosition == null -> Component.translatable("gui.academy.energy_laser_tower.status_unlinked").string
            blockEntity.energyStored <= 0 -> Component.translatable("gui.academy.energy_laser_tower.status_nopower").string
            else -> Component.translatable("gui.academy.energy_laser_tower.status_ok").string
        }
        feedValueLabel.text = Component.translatable(
            when {
                blockEntity.isStrikeAiming -> "gui.academy.energy_laser_tower.feed_strike"
                blockEntity.isOrbiting && blockEntity.isOrbitHyper -> "gui.academy.energy_laser_tower.feed_hyper"
                blockEntity.isOrbiting -> "gui.academy.energy_laser_tower.feed_orbit"
                blockEntity.isBeamActive -> "gui.academy.energy_laser_tower.feed_beam"
                else -> "gui.academy.energy_laser_tower.feed_idle"
            }
        ).string
    }

    private fun viewerOwnsTower(): Boolean {
        val player = Minecraft.getInstance().player
        return menu.viewerIsOwner() || (player != null && blockEntity.isOwner(player))
    }

    companion object {
        fun create(
            menu: EnergyLaserTowerMenu,
            playerInventory: Inventory,
            title: Component,
            mainPos: BlockPos
        ): EnergyLaserTowerScreen? {
            val entity = Minecraft.getInstance().level?.getBlockEntity(mainPos)
            return if (entity is EnergyLaserTowerBlockEntity) {
                EnergyLaserTowerScreen(menu, playerInventory, title, entity)
            } else {
                null
            }
        }
    }
}
