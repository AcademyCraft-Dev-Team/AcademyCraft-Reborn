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
        MisakaMachineUi.playOpenReveal(hint, 1f, duration, childDuration)

        val opsPage = opsUi.createOpsPage()
        opsPage.visibility = Widget.Visibility.GONE
        opsPage.isEnabled = false
        content.addChild("page_ops", opsPage)

        val wirelessPage = WirelessPanelUtil.create(mainPos, true)
        wirelessPage.visibility = Widget.Visibility.GONE
        wirelessPage.isEnabled = false
        content.addChild("page_wireless", wirelessPage)

        val opsButton = createButton(R.textures.gui.icon.icon_settings)
        MisakaMachineUi.sizeRailButton(opsButton)
        pageButtons.addChild("ops", opsButton)

        val wirelessButton = createButton(R.textures.gui.icon.icon_wireless)
        MisakaMachineUi.sizeRailButton(wirelessButton)
        pageButtons.addChild("wireless", wirelessButton)

        fun showCabinPage(active: FrameLayoutWidget) {
            listOf(invPage, opsPage, wirelessPage).forEach { page ->
                page.cancelAnimations()
                if (page === active) {
                    page.visibility = Widget.Visibility.VISIBLE
                    page.isEnabled = true
                    page.alpha = 1f
                    page.translationY = 0f
                } else {
                    page.visibility = Widget.Visibility.GONE
                    page.isEnabled = false
                    page.alpha = 0f
                    page.translationY = 0f
                }
            }
            active.requestLayout()
        }

        pageButtons.onSelectionChanged = {
            when (it.name) {
                "inv" -> {
                    opsUi.closeOpsDetail(immediate = true)
                    opsUi.closeAssetPane(immediate = true)
                    showCabinPage(invPage)
                    isHandleContainer = true
                    isRenderInventory = true
                }
                "ops" -> {
                    showCabinPage(opsPage)
                    opsUi.onOpsPageShown()
                    isHandleContainer = false
                    isRenderInventory = false
                }
                "wireless" -> {
                    opsUi.closeOpsDetail(immediate = true)
                    opsUi.closeAssetPane(immediate = true)
                    showCabinPage(wirelessPage)
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
        }
    }

    override fun containerTick() {
        super.containerTick()
        energyValueLabel.text = "${blockEntity.energyStored} / ${blockEntity.maxEnergyStorage} AF"
        opsUi.onContainerTick()
    }

    override fun createActionButton(labelKey: String, buttonId: Int): ButtonWidget {
        return MisakaMachineUi.menuActionButton(menu, labelKey, buttonId)
    }

    override fun createLocalActionButton(labelKey: String, onClick: () -> Unit): ButtonWidget {
        return MisakaMachineUi.localActionButton(labelKey, onClick = onClick)
    }

    companion object {
        fun create(
            menu: AerospaceSignalCabinMenu,
            playerInventory: Inventory,
            title: Component,
            mainPos: BlockPos
        ): AerospaceSignalCabinScreen? {
            val entity = Minecraft.getInstance().level?.getBlockEntity(mainPos)
            // Keep the factory menu so server-synced ContainerData (owner / manage tier)
            // is not discarded by replacing player.containerMenu after open.
            return if (entity is AerospaceSignalCabinBlockEntity) {
                AerospaceSignalCabinScreen(menu, playerInventory, title, entity)
            } else {
                null
            }
        }
    }
}
