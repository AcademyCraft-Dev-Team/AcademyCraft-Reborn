package org.academy.internal.client.gui.screen

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.screen.ContainerUiScreen
import org.academy.api.client.gui.util.WirelessPanelUtil.create
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
    private lateinit var energyLabel: LabelWidget

    override fun onInit(
        pageButtons: RadioGroupWidget,
        invButton: RadioButtonWidget,
        content: FrameLayoutWidget,
        invPage: FrameLayoutWidget
    ) {
        val duration = 600L
        val childDuration = duration - 100

        energyLabel = LabelWidget("0 AF")
        energyLabel.layoutParams = FrameLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(16f)
            .gravity(Gravity.TOP)
            .margin(8f, 24f, 8f, 0f)
        invPage.addChild("energy", energyLabel)

        invPage.addChild("hint", LabelWidget(Component.translatable("gui.academy.energy_laser_tower.hint").string).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(16f)
                .gravity(Gravity.TOP)
                .margin(8f, 8f, 8f, 0f)
        })

        val wirelessPage = create(mainPos, true)
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
        val energy = "${blockEntity.energyStored} / ${blockEntity.maxEnergyStorage} AF"
        val status = when {
            !blockEntity.hasClearSky() -> Component.translatable("gui.academy.energy_laser_tower.status_blocked").string
            blockEntity.connectedNodePosition == null -> Component.translatable("gui.academy.energy_laser_tower.status_unlinked").string
            blockEntity.energyStored <= 0 -> Component.translatable("gui.academy.energy_laser_tower.status_nopower").string
            else -> Component.translatable("gui.academy.energy_laser_tower.status_ok").string
        }
        energyLabel.text = "$energy | $status"
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
