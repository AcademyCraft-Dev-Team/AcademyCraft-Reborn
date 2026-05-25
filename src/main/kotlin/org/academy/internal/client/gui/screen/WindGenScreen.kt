package org.academy.internal.client.gui.screen

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.neoforged.bus.api.SubscribeEvent
import org.academy.api.client.Resource
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.screen.ContainerUiScreen
import org.academy.api.client.gui.util.InfoAreaUtil.create
import org.academy.api.client.gui.util.InfoAreaUtil.createAttributeRow
import org.academy.api.client.gui.util.InfoAreaUtil.createInfoRow
import org.academy.api.client.gui.util.WirelessPanelUtil.create
import org.academy.api.client.gui.widget.*
import org.academy.api.client.gui.widget.TextBoxWidget.FocusGainedEvent
import org.academy.api.client.gui.widget.TextBoxWidget.FocusLostEvent
import org.academy.api.client.util.AnimationUtil
import org.academy.internal.common.world.inventory.WindGenMenu
import org.academy.internal.common.world.level.block.entity.WindGenBaseBlockEntity
import java.util.function.Consumer

class WindGenScreen(
    menu: WindGenMenu,
    playerInventory: Inventory,
    title: Component,
    val blockEntity: WindGenBaseBlockEntity
) : ContainerUiScreen<WindGenMenu>(menu, playerInventory, title) {
    private val mainPos: BlockPos = blockEntity.blockPos
    private var topAlphaSetter = Consumer { `_`: Float -> }
    private var pillarAlphaSetter = Consumer { `_`: Float -> }
    private var baseAlphaSetter = Consumer { `_`: Float -> }
    private var bufferValueSetter = Consumer { `_`: String -> }

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

        val effect = FrameLayoutWidget()
        effect.layoutParams = FrameLayoutWidget.LayoutParams()
            .heightMode(SizeMode.MATCH_PARENT)
            .width(24f)
            .gravity(Gravity.CENTER_HORIZONTAL)
            .padding(0f, 12f, 0f, 103f)

        invPage.addChild("effect", effect)
        run {
            val topIcon = ImageWidget(Resource.Textures.ICON_WIND_GEN_TOP)
            topAlphaSetter = { topIcon.alpha = it }
            topIcon.layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .padding(0f, 0f, 0f, 48f)

            effect.addChild("icon_top", topIcon)

            val pillarIcon = ImageWidget(Resource.Textures.ICON_WIND_GEN_PILLAR)
            pillarAlphaSetter = { pillarIcon.alpha = it }
            pillarIcon.layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .padding(0f, 18f, 0f, 30f)

            effect.addChild("icon_pillar", pillarIcon)

            val baseIcon = ImageWidget(Resource.Textures.ICON_WIND_GEN_BASE)
            baseAlphaSetter = { baseIcon.alpha = it }
            baseIcon.layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .padding(0f, 36f, 0f, 12f)
            effect.addChild("icon_base", baseIcon)
        }

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

        val info = create(this, (leftPos + imageWidth).toFloat(), (topPos - 22).toFloat())
        run {
            val p = WidgetContainer.LayoutParams()
                .gravity(Gravity.CENTER_RIGHT)
            val bufferValueLabel = LabelWidget("0 AF")
            bufferValueLabel.layoutParams = p
            bufferValueSetter = { bufferValueLabel.text = it }
            val bufferLayout = createInfoRow("BUFFER", "icon_buffer", -0xda3b01, bufferValueLabel)
            info.addChild("energy_layout", bufferLayout)

            val infoLabel = LabelWidget("Information")
            infoLabel.layoutParams = LinearLayoutWidget.LayoutParams()
                .padding(6.5f, 0f, 0f, 0f)

            infoLabel.scale = 0.75f
            info.addChild("label_info", infoLabel)

            val altitudeValue = blockEntity.altitude.toString() + ""
            val altitudeValueLabel = LabelWidget(altitudeValue)
            altitudeValueLabel.layoutParams = WidgetContainer.LayoutParams()
                .gravity(Gravity.CENTER_RIGHT)
                .size(12f, 12f)

            val altitudeLayout = createAttributeRow("Altitude", altitudeValueLabel)
            info.addChild("altitude_layout", altitudeLayout)
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
        bufferValueSetter.accept(String.format(AF, blockEntity.energyStored))

        when (blockEntity.completeness) {
            WindGenBaseBlockEntity.Completeness.NO_TOP -> {
                baseAlphaSetter.accept(1f)
                pillarAlphaSetter.accept(1f)
                topAlphaSetter.accept(0.2f)
            }

            WindGenBaseBlockEntity.Completeness.BASE_ONLY -> {
                baseAlphaSetter.accept(1f)
                pillarAlphaSetter.accept(0.2f)
                topAlphaSetter.accept(0.2f)
            }

            WindGenBaseBlockEntity.Completeness.COMPLETE -> {
                baseAlphaSetter.accept(1f)
                pillarAlphaSetter.accept(1f)
                topAlphaSetter.accept(1f)
            }

            WindGenBaseBlockEntity.Completeness.COMPLETE_NOT_WORKING -> {
                baseAlphaSetter.accept(1f)
                pillarAlphaSetter.accept(1f)
                topAlphaSetter.accept(0.6f)
            }
        }
    }

    override fun containerTick() {
        super.containerTick()
        updateInfo()
    }

    @SubscribeEvent
    fun onFocusGainedEvent(event: FocusGainedEvent) {
        isHandleContainer = false
    }

    @SubscribeEvent
    fun onFocusLostEvent(event: FocusLostEvent) {
        isHandleContainer = true
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