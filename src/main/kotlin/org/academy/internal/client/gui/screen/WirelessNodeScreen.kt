package org.academy.internal.client.gui.screen

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import org.academy.api.client.Resource
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.msdf.atlas.MsdfAtlasDebugger
import org.academy.api.client.gui.msdf.font.MsdfFontService
import org.academy.api.client.gui.screen.ContainerUiScreen
import org.academy.api.client.gui.util.InfoAreaUtil.create
import org.academy.api.client.gui.util.InfoAreaUtil.createAttributeRow
import org.academy.api.client.gui.util.InfoAreaUtil.createInfoRow
import org.academy.api.client.gui.util.InfoAreaUtil.createInputRow
import org.academy.api.client.gui.util.WirelessPanelUtil.create
import org.academy.api.client.gui.widget.*
import org.academy.api.client.gui.widget.TextBoxWidget.FocusGainedEvent
import org.academy.api.client.gui.widget.TextBoxWidget.FocusLostEvent
import org.academy.api.client.util.AnimationUtil
import org.academy.api.common.wireless.SetNodeNamePacket
import org.academy.api.common.wireless.SetNodePassPacket
import org.academy.internal.common.world.inventory.WirelessNodeMenu
import org.academy.internal.common.world.level.block.entity.WirelessNodeBlockEntity
import org.misaka.MisakaNetworkClient
import java.lang.Float
import java.util.function.Consumer
import kotlin.Int
import kotlin.String
import kotlin.run

class WirelessNodeScreen(
    menu: WirelessNodeMenu,
    playerInventory: Inventory,
    title: Component,
    private val wirelessNodeBlockEntity: WirelessNodeBlockEntity
) : ContainerUiScreen<WirelessNodeMenu>(menu, playerInventory, title) {
    private val mainPos: BlockPos = wirelessNodeBlockEntity.blockPos
    private var ticks = 0
    private var energyValueSetter =  { _: String -> }
    private var capacityValueSetter =  { _: String -> }
    private var rangeValueSetter =  { _: String -> }

    init {
        NeoForge.EVENT_BUS.register(this)
/*        for (msdfFont in MsdfFontService.loadedFonts.values) {
            MsdfAtlasDebugger.dumpAtlas(msdfFont.atlas,
                msdfFont.descriptor.identifier.path.length.toString()
            )
        }*/
    }

    override fun onClose() {
        super.onClose()
        NeoForge.EVENT_BUS.unregister(this)
    }

    override fun onInit(
        pageButtons: RadioGroupWidget,
        invButton: RadioButtonWidget,
        content: FrameLayoutWidget,
        invPage: FrameLayoutWidget
    ) {
        val duration = 600L
        val childDuration = duration - 100

        val ui = ImageWidget(Resource.Textures.WIRELESS_NODE_UI)
        ui.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)

        invPage.addChild("ui", ui)

        val effect: SpriteSheetWidget = object : SpriteSheetWidget(
            Resource.Textures.WIRELESS_NODE_STATE,
            Orientation.VERTICAL,
            186, 750,
            186, 75,
            10
        ) {
            override fun tick() {
                var progressCapacity =
                    wirelessNodeBlockEntity.connectedUsersCount.toFloat() / wirelessNodeBlockEntity.maxConnectedUsers

                if (Float.isNaN(progressCapacity)) progressCapacity = 0f
                val index: Int = if (wirelessNodeBlockEntity.connectedUsersCount == 0) {
                    if ((ticks / 20) % 2 == 0) 8 else 9
                } else {
                    Math.clamp((progressCapacity * 8 - 1).toInt().toLong(), 0, 7)
                }

                frameIndex = index
            }
        }
        effect.layoutParams = FrameLayoutWidget.LayoutParams()
            .heightMode(SizeMode.MATCH_PARENT)
            .width(186 / 2f)
            .gravity(Gravity.CENTER_HORIZONTAL)
            .padding(0f, 33.5f, 0f, 116f)

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

        val info = create(this, (leftPos + imageWidth).toFloat(), (topPos - 22).toFloat())
        run {
            val p = WidgetContainer.LayoutParams()
                .gravity(Gravity.CENTER_RIGHT)
            val energyValueLabel = LabelWidget("0 AF")
            energyValueLabel.layoutParams = p
            energyValueSetter = { energyValueLabel.text = it }
            val energyLayout = createInfoRow("ENERGY", "icon_energy", -0xda3b01, energyValueLabel)
            info.addChild("energy_layout", energyLayout)

            val capacityValueLabel = LabelWidget("0 / 0")
            capacityValueLabel.layoutParams = p
            capacityValueSetter = { capacityValueLabel.text = it }
            val capacityLayout = createInfoRow("CAPACITY", "icon_capacity", -0x9400, capacityValueLabel)
            info.addChild("capacity_layout", capacityLayout)

            val infoLabel = LabelWidget("Information")
            infoLabel.layoutParams = LinearLayoutWidget.LayoutParams()
                .padding(6.5f, 0f, 0f, 0f)

            infoLabel.scale = 0.75f
            info.addChild("label_info", infoLabel)

            val rangeValueLabel = LabelWidget("0")
            rangeValueSetter = { rangeValueLabel.text = it }
            rangeValueLabel.layoutParams = WidgetContainer.LayoutParams()
                .height(LabelWidget.DEFAULT_BASE_FONT_SIZE)
                .gravity(Gravity.CENTER)

            val range = "Trans. Range"
            val rangeLayout = createAttributeRow(range, rangeValueLabel)
            info.addChild("range_layout", rangeLayout)

            val nameTextBox = TextBoxWidget(12)
            nameTextBox.background = null
            nameTextBox.setWhenEnter { s ->
                MisakaNetworkClient.send(
                    SetNodeNamePacket(wirelessNodeBlockEntity.blockPos, s)
                )
            }
            val name = "Node Name"
            val nameLayout = createAttributeRow(name, createInputRow(nameTextBox))
            info.addChild("name_layout", nameLayout)

            val passTextBox = TextBoxWidget(12)
            passTextBox.background = null
            passTextBox.setWhenEnter { s ->
                MisakaNetworkClient.send(
                    SetNodePassPacket(wirelessNodeBlockEntity.blockPos, s)
                )
            }
            val pass = "Password"
            val passLayout = createAttributeRow(pass, createInputRow(passTextBox))
            info.addChild("pass_layout", passLayout)
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

    @SubscribeEvent
    fun onFocusGainedEvent(event: FocusGainedEvent) {
        isHandleContainer = false
    }

    @SubscribeEvent
    fun onFocusLostEvent(event: FocusLostEvent) {
        isHandleContainer = true
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