package org.academy.internal.client.gui.screen

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.screen.ContainerUiScreen
import org.academy.api.client.gui.util.WirelessPanelUtil.create
import org.academy.api.client.gui.widget.*
import org.academy.api.client.resources.R
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

        val ui = ImageWidget(R.textures.gui.element.ui_gen)
        ui.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)

        invPage.addChild("ui", ui)

        val effect: SpriteSheetWidget = SpriteSheetWidget(
            R.textures.gui.solar_gen.icon_solar_gen_sunny,
            Orientation.VERTICAL,
            48, 96,
            48, 48,
            2
        ).apply {
            var lastTime = System.currentTimeMillis()
            setFrameUpdate {
                val time = System.currentTimeMillis()
                if (time - lastTime > 500) {
                    nextFrame()
                    lastTime = time
                }
                true
            }
        }
        stateConsumer = {
            effect.setTexture(
                when (it) {
                    SolarGenBlockEntity.State.SUNNY -> R.textures.gui.solar_gen.icon_solar_gen_sunny
                    SolarGenBlockEntity.State.RAINY -> R.textures.gui.solar_gen.icon_solar_gen_rainy
                    SolarGenBlockEntity.State.NIGHT -> R.textures.gui.solar_gen.icon_solar_gen_night
                }
            )
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
