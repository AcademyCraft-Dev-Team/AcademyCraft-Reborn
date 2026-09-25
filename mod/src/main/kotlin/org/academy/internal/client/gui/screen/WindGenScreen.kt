package org.academy.internal.client.gui.screen

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.dsl.*
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.screen.ContainerUiScreen
import org.academy.api.client.gui.util.attributeRow
import org.academy.api.client.gui.util.infoArea
import org.academy.api.client.gui.util.infoRow
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.RadioButtonWidget
import org.academy.api.client.gui.widget.RadioGroupWidget
import org.academy.api.client.resources.R
import org.academy.internal.common.world.inventory.WindGenMenu
import org.academy.internal.common.world.level.block.entity.WindGenBaseBlockEntity

class WindGenScreen(
    menu: WindGenMenu,
    playerInventory: Inventory,
    title: Component,
    val blockEntity: WindGenBaseBlockEntity
) : ContainerUiScreen<WindGenMenu>(menu, playerInventory, title) {
    private val mainPos: BlockPos = blockEntity.blockPos
    private var topAlphaSetter: (Float) -> Unit = {}
    private var pillarAlphaSetter: (Float) -> Unit = {}
    private var baseAlphaSetter: (Float) -> Unit = {}
    private var bufferValueSetter: (String) -> Unit = {}

    override fun onInit(
        pageButtons: RadioGroupWidget,
        invButton: RadioButtonWidget,
        content: FrameLayoutWidget,
        invPage: FrameLayoutWidget
    ) {
        val duration = 600L
        val childDuration = duration - 100

        invPage.image(R.textures.gui.element.ui_gen, "ui") {
            matchParent()
        }

        val effect = invPage.frame("effect") {
            lp {
                heightMode(SizeMode.MATCH_PARENT)
                width(24f)
                gravity(Gravity.CENTER_HORIZONTAL)
                padding(0f, 12f, 0f, 103f)
            }
        }

        val topIcon = effect.image(R.textures.gui.wind_gen.icon_wind_top, "icon_top") {
            lp {
                sizeMode(SizeMode.MATCH_PARENT)
                padding(0f, 0f, 0f, 48f)
            }
        }
        topAlphaSetter = { topIcon.alpha = it }

        val pillarIcon = effect.image(R.textures.gui.wind_gen.icon_wind_pillar, "icon_pillar") {
            lp {
                sizeMode(SizeMode.MATCH_PARENT)
                padding(0f, 18f, 0f, 30f)
            }
        }
        pillarAlphaSetter = { pillarIcon.alpha = it }

        val baseIcon = effect.image(R.textures.gui.wind_gen.icon_wind_base, "icon_base") {
            lp {
                sizeMode(SizeMode.MATCH_PARENT)
                padding(0f, 36f, 0f, 12f)
            }
        }
        baseAlphaSetter = { baseIcon.alpha = it }

        setupWirelessPage(
            pageButtons,
            invButton,
            content,
            invPage,
            mainPos,
            createButton(R.textures.gui.icon.icon_wireless)
        )

        root.infoArea((leftPos + imageWidth).toFloat(), (topPos - 22).toFloat()) {
            val bufferValueLabel = infoRow("BUFFER", "icon_buffer", -0xda3b01, "0 AF")
            bufferValueSetter = { bufferValueLabel.text = it }

            text("Information", "label_info") {
                lp {
                    paddingLeft(8f)
                }
            }

            attributeRow("Altitude") {
                text(blockEntity.altitude.toString(), "altitude_value") {
                    lp {
                        gravity(Gravity.CENTER_RIGHT)
                        size(12f, 12f)
                    }
                    gravity = Gravity.CENTER_RIGHT
                }
            }
        }

        pageButtons.startAnimation(
            ObjectAnimator.ofFloat({ pageButtons.alpha = it }, 0f, 1f).setDuration(childDuration)
        )
        pageButtons.startAnimation(
            ObjectAnimator.ofFloat(
                { pageButtons.translationY = it }, 20f, 0f
            ).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_CUBIC)
        )

        updateInfo()
    }

    private fun updateInfo() {
        bufferValueSetter(AF.format(blockEntity.energyStored))

        when (blockEntity.completeness) {
            WindGenBaseBlockEntity.Completeness.NO_TOP -> {
                baseAlphaSetter(1f)
                pillarAlphaSetter(1f)
                topAlphaSetter(0.2f)
            }

            WindGenBaseBlockEntity.Completeness.BASE_ONLY -> {
                baseAlphaSetter(1f)
                pillarAlphaSetter(0.2f)
                topAlphaSetter(0.2f)
            }

            WindGenBaseBlockEntity.Completeness.COMPLETE -> {
                baseAlphaSetter(1f)
                pillarAlphaSetter(1f)
                topAlphaSetter(1f)
            }

            WindGenBaseBlockEntity.Completeness.COMPLETE_NOT_WORKING -> {
                baseAlphaSetter(1f)
                pillarAlphaSetter(1f)
                topAlphaSetter(0.6f)
            }
        }
    }

    override fun containerTick() {
        super.containerTick()
        updateInfo()
    }

    companion object {
        const val AF: String = "%d AF"
        fun create(menu: WindGenMenu, playerInventory: Inventory, title: Component, mainPos: BlockPos): WindGenScreen? {
            val level = Minecraft.getInstance().level
            val entity = level?.getBlockEntity(mainPos)
            return if (entity is WindGenBaseBlockEntity) {
                WindGenScreen(menu, playerInventory, title, entity)
            } else {
                null
            }
        }
    }
}
