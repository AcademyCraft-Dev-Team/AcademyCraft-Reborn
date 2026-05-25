package org.academy.api.client.gui.util

import net.minecraft.core.BlockPos
import org.academy.api.client.Resource
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.drawable.TextureDrawable
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.*
import org.academy.api.common.wireless.ConnectNodePacket
import org.academy.api.common.wireless.DisconnectNodePacket
import org.academy.api.common.wireless.GetAvailableNodesPacket
import org.academy.api.common.wireless.GetCurrentNodePacket
import org.misaka.MisakaNetworkClient
import java.util.function.Consumer

object WirelessPanelUtil {
    const val PANEL_WIDTH: Float = 176.0f
    const val PANEL_HEIGHT: Float = 187.0f

    private const val MARGIN_HORIZONTAL = 12.0f
    private const val MARGIN_VERTICAL = 10.0f
    private const val SPACING_MAJOR = 8.0f
    private const val SPACING_MINOR = 4.0f
    private const val LIST_ITEM_HEIGHT = 18.0f
    private const val SCROLLBAR_WIDTH = 5.0f

    fun create(position: BlockPos, withBackground: Boolean): FrameLayoutWidget {
        val root = FrameLayoutWidget()
        root.layoutParams = WidgetContainer.LayoutParams()
            .size(PANEL_WIDTH, PANEL_HEIGHT)
        run {
            if (withBackground) {
                val back = BlendQuadWidget()
                back.layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                back.alpha = 0.5f
                root.addChild("back", back)
            }
            val content = LinearLayoutWidget()
            content.setOrientation(Orientation.VERTICAL)
            content.layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .padding(WirelessPanelUtil.MARGIN_HORIZONTAL, WirelessPanelUtil.MARGIN_VERTICAL)
            content.setSpacing(WirelessPanelUtil.SPACING_MINOR)
            root.addChild("content", content)
            run {
                val icon = ImageWidget(Resource.Textures.ICON_OPEN_WIRELESS_PANEL)
                icon.layoutParams = LinearLayoutWidget.LayoutParams()
                    .size(16f, 16f)
                content.addChild("icon", icon)

                val connectedLabel = LabelWidget("Connected")
                content.addChild("connected_node_label", connectedLabel)

                val connectedNodeContainer = FrameLayoutWidget()
                connectedNodeContainer.layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(WirelessPanelUtil.LIST_ITEM_HEIGHT)
                    .marginTop(WirelessPanelUtil.SPACING_MINOR - WirelessPanelUtil.SPACING_MAJOR)
                    .marginRight(WirelessPanelUtil.SCROLLBAR_WIDTH + WirelessPanelUtil.SPACING_MINOR)
                content.addChild("connected_node_container", connectedNodeContainer)

                val availableLabel = LabelWidget("Available")
                content.addChild("available_node_label", availableLabel)

                val listContainer = LinearLayoutWidget()
                listContainer.setOrientation(Orientation.HORIZONTAL)
                listContainer.setSpacing(WirelessPanelUtil.SPACING_MINOR)
                listContainer.layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(0f)
                    .marginTop(WirelessPanelUtil.SPACING_MINOR - WirelessPanelUtil.SPACING_MAJOR)
                content.addChild("list_container", listContainer)
                run {
                    val scrollPanel = ScrollPanelWidget()
                    scrollPanel.layoutParams = LinearLayoutWidget.LayoutParams()
                        .weight(1f)
                        .width(0f)
                        .heightMode(SizeMode.MATCH_PARENT)
                    listContainer.addChild("scroll_panel", scrollPanel)

                    val scrollBar = ScrollBarWidget(scrollPanel, Orientation.VERTICAL)
                    scrollBar.layoutParams = LinearLayoutWidget.LayoutParams()
                        .width(WirelessPanelUtil.SCROLLBAR_WIDTH)
                        .heightMode(SizeMode.MATCH_PARENT)
                    listContainer.addChild("scroll_bar", scrollBar)

                    val nodeList = LinearLayoutWidget()
                    nodeList.setOrientation(Orientation.VERTICAL)
                    nodeList.layoutParams = FrameLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                    scrollPanel.setContent(nodeList)
                    WirelessPanelUtil.updateConnectedNodeDisplay(position, connectedNodeContainer, nodeList)
                }
            }
        }
        return root
    }

    private fun updateConnectedNodeDisplay(
        position: BlockPos,
        connectedNodeContainer: FrameLayoutWidget,
        nodeList: LinearLayoutWidget
    ) {
        val requestPayload = GetCurrentNodePacket(position)
        MisakaNetworkClient.FUTURE_MANAGER.sendRequestToServer(
            requestPayload,
            Consumer { response: GetCurrentNodePacket.Response? ->
                if (response == null) return@Consumer
                connectedNodeContainer.clearChildren()
                val nodeName = response.nodeName
                val connectedNodeWidget =
                    getNodeWidget(position, connectedNodeContainer, nodeList, nodeName, true, response.isNull)
                connectedNodeWidget.layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                connectedNodeContainer.addChild("connected_node", connectedNodeWidget)
                updateAvailableNodesList(position, nodeName, connectedNodeContainer, nodeList)
            })
    }

    private fun updateAvailableNodesList(
        position: BlockPos,
        connectedNodeName: String,
        connectedNodeContainer: FrameLayoutWidget,
        nodeList: LinearLayoutWidget
    ) {
        val requestPayload = GetAvailableNodesPacket(position)
        MisakaNetworkClient.FUTURE_MANAGER.sendRequestToServer(
            requestPayload,
            Consumer { response ->
                if (response == null) return@Consumer
                nodeList.clearChildren()
                val availableNodes = response.availableNodeNames
                availableNodes.removeIf { s -> s == connectedNodeName }
                for (name in availableNodes) {
                    val nodeViewPanel = getNodeWidget(
                        position,
                        connectedNodeContainer,
                        nodeList,
                        name,
                        isConnected = false,
                        isNull = false
                    )
                    nodeViewPanel.layoutParams = LinearLayoutWidget.LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                        .height(LIST_ITEM_HEIGHT)
                    nodeList.addChild("node_$name", nodeViewPanel)
                }
            })
    }

    private fun getNodeWidget(
        position: BlockPos,
        connectedNodeContainer: FrameLayoutWidget,
        nodeList: LinearLayoutWidget,
        nodeName: String,
        isConnected: Boolean,
        isNull: Boolean
    ): FrameLayoutWidget {
        val nodeViewPanel = FrameLayoutWidget()
        run {
            val nodeBack = FillWidget(-0x1)
            nodeBack.layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .padding(2f, 2f)
            nodeBack.alpha = 0.25f
            nodeViewPanel.addChild("back", nodeBack)

            val itemContent = LinearLayoutWidget()
            itemContent.setOrientation(Orientation.HORIZONTAL)
            itemContent.layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER_VERTICAL)
                .paddingHorizontal(4f)
            itemContent.setSpacing(4f)
            nodeViewPanel.addChild("content", itemContent)
            run {
                val nodeIcon = ImageWidget(Resource.Textures.ICON_NODE)
                nodeIcon.layoutParams = LinearLayoutWidget.LayoutParams()
                    .gravity(Gravity.CENTER)
                    .size(14f, 14f)
                itemContent.addChild("icon", nodeIcon)

                val nodeNameLabel = LabelWidget(nodeName)
                nodeNameLabel.layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .height(10f)
                    .gravity(Gravity.CENTER_VERTICAL)
                itemContent.addChild("node_name", nodeNameLabel)
                if (!isConnected) {
                    val connectAction =  { password: String ->
                        MisakaNetworkClient.sendPacket(
                            ConnectNodePacket(
                                position,
                                nodeName,
                                password
                            )
                        )
                        WirelessPanelUtil.updateConnectedNodeDisplay(position, connectedNodeContainer, nodeList)
                    }
                    val inputBox = TextBoxWidget(12)
                    inputBox.layoutParams = LinearLayoutWidget.LayoutParams()
                        .gravity(Gravity.CENTER)
                        .size(46f, 10f)
                    inputBox.setWhenEnter(connectAction)
                    itemContent.addChild("input", inputBox)

                    val connectButton = ButtonWidget()
                    connectButton.layoutParams = LinearLayoutWidget.LayoutParams()
                        .gravity(Gravity.CENTER)
                        .size(14f, 14f)
                    connectButton.onClickListener = { _ -> connectAction(inputBox.text) }
                    itemContent.addChild("button", connectButton)
                    run {
                        val content = ImageWidget()
                        val defaultDrawable = TextureDrawable(Resource.Textures.ICON_UNCONNECTED)
                        defaultDrawable.tintColor = -0x444445

                        val hoveredDrawable = TextureDrawable(Resource.Textures.ICON_UNCONNECTED)
                        hoveredDrawable.tintColor = -0x1

                        val sld = StateListDrawable()
                        sld.setDefault(defaultDrawable)
                        sld.addState(Widget.HOVERED, hoveredDrawable)

                        content.background = sld
                        content.layoutParams = FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT)
                        connectButton.addChild("content", content)
                    }
                } else if (!isNull) {
                    val disconnectButton = ButtonWidget()
                    disconnectButton.onClickListener = { _ ->
                        MisakaNetworkClient.sendPacket(DisconnectNodePacket(position))
                        WirelessPanelUtil.updateConnectedNodeDisplay(position, connectedNodeContainer, nodeList)
                    }
                    disconnectButton.layoutParams = LinearLayoutWidget.LayoutParams()
                        .gravity(Gravity.CENTER)
                        .size(14f, 14f)
                    itemContent.addChild("button", disconnectButton)
                    run {
                        val content = ImageWidget()
                        val defaultDrawable = TextureDrawable(Resource.Textures.ICON_CONNECTED)
                        defaultDrawable.tintColor = -0x444445

                        val hoveredDrawable = TextureDrawable(Resource.Textures.ICON_CONNECTED)
                        hoveredDrawable.tintColor = -0x1

                        val sld = StateListDrawable()
                        sld.setDefault(defaultDrawable)
                        sld.addState(Widget.HOVERED, hoveredDrawable)

                        content.background = sld
                        content.layoutParams = FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT)
                        disconnectButton.addChild("content", content)
                    }
                }
            }
        }
        return nodeViewPanel
    }
}