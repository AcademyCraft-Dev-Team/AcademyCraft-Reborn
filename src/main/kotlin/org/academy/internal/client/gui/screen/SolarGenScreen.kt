package org.academy.internal.client.gui.screen

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import org.academy.api.client.Resource
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.screen.ContainerUiScreen
import org.academy.api.client.gui.util.WirelessPanelUtil.create
import org.academy.api.client.gui.widget.*
import org.academy.api.client.util.AnimationUtil
import org.academy.internal.common.world.inventory.SolarGenMenu
import org.academy.internal.common.world.level.block.entity.SolarGenBlockEntity
import java.util.function.Consumer

class SolarGenScreen private constructor(
    menu: SolarGenMenu,
    playerInventory: Inventory,
    title: Component,
    private val blockEntity: SolarGenBlockEntity
) : ContainerUiScreen<SolarGenMenu>(menu, playerInventory, title) {
    private val mainPos: BlockPos = blockEntity.blockPos
    private var stateConsumer = Consumer { `_`: SolarGenBlockEntity.State -> }

    override fun onInit(
        pageButtons: RadioGroupWidget,
        invButton: RadioButtonWidget,
        content: FrameLayoutWidget,
        invPage: FrameLayoutWidget
    ) {
        val duration = 600L
        val childDuration = duration - 100

        val ui = ImageWidget(Resource.Textures.UI_GEN)
        ui.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)

        invPage.addChild("ui", ui)

        val effect: SpriteSheetWidget = object : SpriteSheetWidget(
            Resource.Textures.ICON_SOLAR_GEN_SUNNY,
            Orientation.VERTICAL,
            48, 96,
            48, 48,
            2
        ) {
            private var ticks = 0

            override fun tick() {
                ticks++
                if (ticks == 10) {
                    nextFrame()
                    ticks = 0
                }
            }
        }
        stateConsumer = Consumer { state: SolarGenBlockEntity.State? ->
            when (state) {
                SolarGenBlockEntity.State.SUNNY -> effect.setTexture(
                    Resource.Textures.ICON_SOLAR_GEN_SUNNY
                )

                SolarGenBlockEntity.State.RAINY -> effect.setTexture(Resource.Textures.ICON_SOLAR_GEN_RAINY)
                SolarGenBlockEntity.State.NIGHT -> effect.setTexture(Resource.Textures.ICON_SOLAR_GEN_NIGHT)
                else -> {}
            }
        }
        effect.layoutParams = FrameLayoutWidget.LayoutParams()
            .heightMode(SizeMode.MATCH_PARENT)
            .width(48f)
            .gravity(Gravity.CENTER_HORIZONTAL)
            .padding(0f, 21f, 0f, 118f)

        invPage.addChild("effect", effect)

        val wirelessPage = create(mainPos, true)
        wirelessPage.visibility = Widget.Visibility.GONE
        wirelessPage.isEnabled = false
        content.addChild("page_wireless", wirelessPage)

        val wirelessButton = createButton(Resource.Textures.ICON_WIRELESS)
        wirelessButton.layoutParams = WidgetContainer.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(16f)

        pageButtons.addChild("wireless", wirelessButton)
        pageButtons.onSelectionChanged = Consumer { button: RadioButtonWidget? ->
            when (button!!.name) {
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
            ObjectAnimator.ofFloat
                (
                { pageButtons.translationY = it }, 20f, 0f
            ).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_CUBIC)
        )
    }

    override fun containerTick() {
        super.containerTick()
        stateConsumer.accept(blockEntity.state)
    }

    companion object {
        fun create(
            menu: SolarGenMenu,
            playerInventory: Inventory,
            title: Component,
            mainPos: BlockPos
        ): SolarGenScreen? {
            val level = Minecraft.getInstance().level
            val entity = level?.getBlockEntity(mainPos)
            return if (entity is SolarGenBlockEntity) {
                SolarGenScreen(menu, playerInventory, title, entity)
            } else {
                null
            }
        }
    }
}