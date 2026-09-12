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
import org.academy.internal.common.world.inventory.AerospaceSignalCabinMenu
import org.academy.internal.common.world.level.block.entity.AerospaceSignalCabinBlockEntity

class AerospaceSignalCabinScreen private constructor(
    menu: AerospaceSignalCabinMenu,
    playerInventory: Inventory,
    title: Component,
    override val blockEntity: AerospaceSignalCabinBlockEntity
) : ContainerUiScreen<AerospaceSignalCabinMenu>(menu, playerInventory, title), AerospaceCabinOpsUi.Host {
    private val mainPos: BlockPos = blockEntity.blockPos
    private lateinit var energyValueLabel: LabelWidget
    private val opsUi = AerospaceCabinOpsUi(this)

    override val cabinMenu: AerospaceSignalCabinMenu
        get() = menu

    override val client: Minecraft
        get() = minecraft

    override fun onInit(
        pageButtons: RadioGroupWidget,
        invButton: RadioButtonWidget,
        content: FrameLayoutWidget,
        invPage: FrameLayoutWidget
    ) {
        val duration = 600L
        val childDuration = duration - 100

        val hint = LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.hint").string).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(12f)
                .gravity(Gravity.TOP)
                .margin(8f, 2f, 8f, 0f)
            scale = 0.75f
        }
        invPage.addChild("hint", hint)
        playOpenReveal(hint, 1f, duration, childDuration)

        val opsPage = opsUi.createOpsPage()
        opsPage.visibility = Widget.Visibility.GONE
        opsPage.isEnabled = false
        content.addChild("page_ops", opsPage)

        val wirelessPage = WirelessPanelUtil.create(mainPos, true)
        wirelessPage.visibility = Widget.Visibility.GONE
        wirelessPage.isEnabled = false
        content.addChild("page_wireless", wirelessPage)

        val opsButton = createButton(R.textures.gui.icon.icon_settings)
        opsButton.layoutParams = WidgetContainer.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(16f)
        pageButtons.addChild("ops", opsButton)

        val wirelessButton = createButton(R.textures.gui.icon.icon_wireless)
        wirelessButton.layoutParams = WidgetContainer.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(16f)
        pageButtons.addChild("wireless", wirelessButton)

        pageButtons.onSelectionChanged = {
            when (it.name) {
                "inv" -> {
                    opsUi.closeOpsDetail(immediate = true)
                    AnimationUtil.hide(opsPage)
                    AnimationUtil.hide(wirelessPage)
                    AnimationUtil.show(invPage)
                    isHandleContainer = true
                    isRenderInventory = true
                }
                "ops" -> {
                    opsUi.closeOpsDetail(immediate = true)
                    AnimationUtil.hide(invPage)
                    AnimationUtil.hide(wirelessPage)
                    AnimationUtil.show(opsPage)
                    isHandleContainer = false
                    isRenderInventory = false
                }
                "wireless" -> {
                    opsUi.closeOpsDetail(immediate = true)
                    AnimationUtil.hide(invPage)
                    AnimationUtil.hide(opsPage)
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
            val p = WidgetContainer.LayoutParams()
                .gravity(Gravity.CENTER_RIGHT)
            energyValueLabel = LabelWidget("0 AF")
            energyValueLabel.layoutParams = p
            val energyLayout = InfoAreaUtil.createInfoRow("ENERGY", "icon_energy", -0xda3b01, energyValueLabel)
            info.addChild("energy_layout", energyLayout)
        }
    }

    /** Same open timing as the left page rail: alpha fade + slide up with the face expand. */
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
        energyValueLabel.text = "${blockEntity.energyStored} / ${blockEntity.maxEnergyStorage} AF"
        opsUi.onContainerTick()
    }

    override fun createActionButton(labelKey: String, buttonId: Int): ButtonWidget {
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

    override fun createLocalActionButton(labelKey: String, onClick: () -> Unit): ButtonWidget {
        val button = ButtonWidget()
        button.onClickListener = OnClickListener { onClick() }
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

    companion object {
        private const val SCALE_BODY = 0.75f

        fun create(
            menu: AerospaceSignalCabinMenu,
            playerInventory: Inventory,
            title: Component,
            mainPos: BlockPos
        ): AerospaceSignalCabinScreen? {
            val minecraft = Minecraft.getInstance()
            val level = minecraft.level ?: return null
            val player = minecraft.player ?: return null
            val entity = level.getBlockEntity(mainPos)
            return if (entity is AerospaceSignalCabinBlockEntity) {
                val boundMenu = AerospaceSignalCabinMenu(
                    menu.containerId,
                    playerInventory,
                    ContainerLevelAccess.create(level, mainPos),
                    entity
                )
                player.containerMenu = boundMenu
                AerospaceSignalCabinScreen(boundMenu, playerInventory, title, entity)
            } else {
                null
            }
        }
    }
}
