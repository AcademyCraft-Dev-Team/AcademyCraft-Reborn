package org.academy.internal.client.gui.screen

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Inventory
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.dsl.*
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.screen.ContainerUiScreen
import org.academy.api.client.gui.util.attributeRow
import org.academy.api.client.gui.util.infoArea
import org.academy.api.client.gui.util.infoRow
import org.academy.api.client.gui.util.inputRow
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.RadioButtonWidget
import org.academy.api.client.gui.widget.RadioGroupWidget
import org.academy.api.client.resources.R
import org.academy.api.common.wireless.SetNodeNamePacket
import org.academy.api.common.wireless.SetNodePassPacket
import org.academy.internal.common.world.inventory.WirelessNodeMenu
import org.academy.internal.common.world.level.block.entity.WirelessNodeBlockEntity
import org.misaka.MisakaNetworkClient

class WirelessNodeScreen(
    menu: WirelessNodeMenu,
    playerInventory: Inventory,
    title: Component,
    private val wirelessNodeBlockEntity: WirelessNodeBlockEntity
) : ContainerUiScreen<WirelessNodeMenu>(menu, playerInventory, title) {
    private val mainPos: BlockPos = wirelessNodeBlockEntity.blockPos
    private var ticks = 0
    private var energyValueSetter = { _: String -> }
    private var capacityValueSetter = { _: String -> }
    private var rangeValueSetter = { _: String -> }

    override fun onInit(
        pageButtons: RadioGroupWidget,
        invButton: RadioButtonWidget,
        content: FrameLayoutWidget,
        invPage: FrameLayoutWidget
    ) {
        val duration = 600L
        val childDuration = duration - 100

        invPage.image(R.textures.gui.node.ui_node, "ui") {
            matchParent()
        }

        invPage.spriteSheet(
            R.textures.gui.node.state_node,
            Orientation.VERTICAL,
            186, 750,
            186, 75,
            10,
            "effect"
        ) {
            lp {
                matchHeight()
                width(186 / 2f)
                gravity(Gravity.CENTER_HORIZONTAL)
                padding(0f, 33.5f, 0f, 116f)
            }
            setFrameUpdate {
                var progressCapacity =
                    wirelessNodeBlockEntity.connectedUsersCount.toFloat() / wirelessNodeBlockEntity.maxConnectedUsers

                if (progressCapacity.isNaN()) progressCapacity = 0f
                frameIndex = if (wirelessNodeBlockEntity.connectedUsersCount == 0) {
                    if ((ticks / 20) % 2 == 0) 8 else 9
                } else {
                    Mth.clamp((progressCapacity * 8 - 1).toInt(), 0, 7)
                }

                true
            }
        }

        setupWirelessPage(
            pageButtons,
            invButton,
            content,
            invPage,
            mainPos,
            createButton(R.textures.gui.icon.icon_wireless)
        )

        root.infoArea((leftPos + imageWidth).toFloat(), (topPos - 22).toFloat()) {
            val energyValueLabel = infoRow("ENERGY", "icon_energy", -0xda3b01, "0 AF")
            energyValueSetter = { energyValueLabel.text = it }

            val capacityValueLabel = infoRow("CAPACITY", "icon_capacity", -0x9400, "0 / 0")
            capacityValueSetter = { capacityValueLabel.text = it }

            text("Information", "label_info") {
                lp { padding(8f, 0f, 0f, 0f) }
            }

            attributeRow("Trans. Range") {
                val rangeValueLabel = text("0", "range_value") {
                    lp {
                        gravity(Gravity.CENTER)
                    }
                    gravity = Gravity.CENTER
                }
                rangeValueSetter = { rangeValueLabel.text = it }
            }

            attributeRow("Node Name") {
                inputRow(12, "name_text_box") {
                    background = null
                    enter { value ->
                        MisakaNetworkClient.send(SetNodeNamePacket(wirelessNodeBlockEntity.blockPos, value))
                    }
                }
            }

            attributeRow("Password") {
                inputRow(12, "pass_text_box") {
                    background = null
                    enter { value ->
                        MisakaNetworkClient.send(SetNodePassPacket(wirelessNodeBlockEntity.blockPos, value))
                    }
                }
            }
        }

        pageButtons.startAnimation(
            ObjectAnimator.ofFloat(
                { pageButtons.alpha = it }, 0f, 1f
            ).setDuration(childDuration)
        )
        pageButtons.startAnimation(
            ObjectAnimator.ofFloat(
                { pageButtons.translationY = it }, 20f, 0f
            ).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_CUBIC)
        )

        updateInfo()
    }

    private fun updateInfo() {
        ticks++

        capacityValueSetter(wirelessNodeBlockEntity.connectedUsersCount.toString() + " / " + wirelessNodeBlockEntity.maxConnectedUsers)
        energyValueSetter(WindGenScreen.AF.format(wirelessNodeBlockEntity.energyStored))
        rangeValueSetter(wirelessNodeBlockEntity.radius.toString() + "")
    }

    override fun containerTick() {
        super.containerTick()
        updateInfo()
    }

    companion object {
        fun create(
            menu: WirelessNodeMenu,
            playerInventory: Inventory,
            title: Component,
            mainPos: BlockPos
        ): WirelessNodeScreen? {
            val level = Minecraft.getInstance().level
            val entity = level?.getBlockEntity(mainPos)
            return if (entity is WirelessNodeBlockEntity) {
                WirelessNodeScreen(menu, playerInventory, title, entity)
            } else {
                null
            }
        }
    }
}
