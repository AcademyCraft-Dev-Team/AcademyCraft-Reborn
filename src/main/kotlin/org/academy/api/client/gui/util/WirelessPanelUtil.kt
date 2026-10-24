package org.academy.api.client.gui.util

import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.drawable.TextureDrawable
import org.academy.api.client.gui.dsl.*
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.text.model.Ellipsize
import org.academy.api.client.gui.widget.*
import org.academy.api.client.resources.R
import org.academy.api.common.wireless.ConnectNodePacket
import org.academy.api.common.wireless.DisconnectNodePacket
import org.academy.api.common.wireless.GetAvailableNodesPacket
import org.academy.api.common.wireless.GetCurrentNodePacket
import org.misaka.MisakaNetworkClient
import java.util.function.Consumer

object WirelessPanelUtil {
    const val PANEL_WIDTH: Float = 176.0f
    const val PANEL_HEIGHT: Float = 187.0f

    fun create(position: BlockPos, withBackground: Boolean): FrameLayoutWidget =
        FrameLayoutWidget().apply {
            layoutParams = WidgetContainer.LayoutParams().size(PANEL_WIDTH, PANEL_HEIGHT)
            wirelessPanel(position, withBackground)
        }

    /**
     * Shared wireless-style node row (Fill back, icon, name, connect/disconnect).
     * [trailing] is invoked on the content row before action buttons (e.g. password field).
     */
    fun nodeRow(
        nodeName: String,
        isConnected: Boolean,
        isNone: Boolean,
        onConnect: () -> Unit,
        onDisconnect: () -> Unit,
        trailing: (LinearLayoutWidget) -> Unit = {}
    ): FrameLayoutWidget {
        val nodeViewPanel = FrameLayoutWidget()
        val nodeBack = FillWidget(-0x1)
        nodeBack.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)
            .padding(2f, 2f)
        nodeBack.alpha = 0.25f
        nodeViewPanel.addChild("back", nodeBack)

        val itemContent = LinearLayoutWidget()
        itemContent.orientation = Orientation.HORIZONTAL
        itemContent.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)
            .gravity(Gravity.CENTER_VERTICAL)
            .paddingHorizontal(4f)
        itemContent.spacing = 4f
        nodeViewPanel.addChild("content", itemContent)

        val nodeIcon = ImageWidget(R.textures.gui.icon.icon_node)
        nodeIcon.layoutParams = LinearLayoutWidget.LayoutParams()
            .gravity(Gravity.CENTER)
            .size(14f, 14f)
        itemContent.addChild("icon", nodeIcon)

        val nodeNameLabel = TextWidget(nodeName).apply {
            maxLines = 1
            ellipsize = Ellipsize.END
        }
        nodeNameLabel.layoutParams = LinearLayoutWidget.LayoutParams()
            .weight(1f)
            .height(10f)
            .gravity(Gravity.CENTER_VERTICAL)
        itemContent.addChild("node_name", nodeNameLabel)

        trailing(itemContent)

        if (!isConnected) {
            val connectButton = ButtonWidget()
            connectButton.layoutParams = LinearLayoutWidget.LayoutParams()
                .gravity(Gravity.CENTER)
                .size(14f, 14f)
            connectButton.onClickListener = { onConnect() }
            itemContent.addChild("button", connectButton) {
                val content = ImageWidget()
                val defaultDrawable = TextureDrawable(R.textures.gui.icon.icon_unconnected)
                defaultDrawable.tintColor = 0xFFE6E6E6.toInt()
                val hoveredDrawable = TextureDrawable(R.textures.gui.icon.icon_unconnected)
                hoveredDrawable.tintColor = -0x1
                val sld = StateListDrawable()
                sld.setDefault(defaultDrawable)
                sld.addState(Widget.HOVERED, hoveredDrawable)
                sld.addState(Widget.FOCUSED, hoveredDrawable)
                connectButton.background = sld
                content.layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                connectButton.addChild("content", content)
            }
        } else if (!isNone) {
            val disconnectButton = ButtonWidget()
            disconnectButton.onClickListener = { onDisconnect() }
            disconnectButton.layoutParams = LinearLayoutWidget.LayoutParams()
                .gravity(Gravity.CENTER)
                .size(14f, 14f)
            itemContent.addChild("button", disconnectButton) {
                val content = ImageWidget()
                val defaultDrawable = TextureDrawable(R.textures.gui.icon.icon_connected)
                defaultDrawable.tintColor = 0xFFE6E6E6.toInt()
                val hoveredDrawable = TextureDrawable(R.textures.gui.icon.icon_connected)
                hoveredDrawable.tintColor = -0x1
                val sld = StateListDrawable()
                sld.setDefault(defaultDrawable)
                sld.addState(Widget.HOVERED, hoveredDrawable)
                sld.addState(Widget.FOCUSED, hoveredDrawable)
                disconnectButton.background = sld
                content.layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                disconnectButton.addChild("content", content)
            }
        }
        return nodeViewPanel
    }

}

private const val MARGIN_HORIZONTAL = 12.0f
private const val MARGIN_VERTICAL = 10.0f
private const val SPACING_MAJOR = 8.0f
private const val SPACING_MINOR = 4.0f
private const val LIST_ITEM_HEIGHT = 18.0f
private const val SCROLLBAR_WIDTH = 5.0f

fun WidgetContainer.wirelessPanel(
    position: BlockPos,
    withBackground: Boolean = true,
    name: String = nextChildName("wireless_panel"),
    init: FrameLayoutWidget.() -> Unit = {}
): FrameLayoutWidget {
    lateinit var connectedNodeContainer: FrameLayoutWidget
    lateinit var nodeList: LinearLayoutWidget

    val panel = frame(name) {
        lp {
            size(WirelessPanelUtil.PANEL_WIDTH, WirelessPanelUtil.PANEL_HEIGHT)
        }
        if (withBackground) {
            blendQuad("back") {
                matchParent()
                alpha = 0.5f
            }
        }
        column("content", spacing = SPACING_MINOR) {
            lp {
                sizeMode(SizeMode.MATCH_PARENT)
                padding(MARGIN_HORIZONTAL, MARGIN_VERTICAL)
            }
            image(R.textures.gui.icon.icon_tonode, "icon") {
                size(16f, 16f)
            }
            text("Connected", "connected_node_label")

            connectedNodeContainer = frame("connected_node_container") {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    height(LIST_ITEM_HEIGHT)
                    marginTop(SPACING_MINOR - SPACING_MAJOR)
                    marginRight(SCROLLBAR_WIDTH + SPACING_MINOR)
                }
            }

            text("Available", "available_node_label")

            row("list_container", spacing = SPACING_MINOR) {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    height(0f)
                    marginTop(SPACING_MINOR - SPACING_MAJOR)
                }
                weight(1f)

                val scroll = scrollPanel(Orientation.VERTICAL, "scroll_panel") {
                    lp {
                        width(0f)
                        heightMode(SizeMode.MATCH_PARENT)
                    }
                    weight(1f)
                }

                val content = standaloneColumn {
                    lp {
                        sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                    }
                }
                content.name = "node_list"
                scroll.setContent(content)
                nodeList = content

                scrollBar(scroll, Orientation.VERTICAL, "scroll_bar") {
                    lp {
                        width(SCROLLBAR_WIDTH)
                        heightMode(SizeMode.MATCH_PARENT)
                    }
                }
            }
        }
        init()
    }

    updateConnectedNodeDisplay(position, connectedNodeContainer, nodeList)
    return panel
}

private fun updateConnectedNodeDisplay(
    position: BlockPos,
    connectedNodeContainer: FrameLayoutWidget,
    nodeList: LinearLayoutWidget
) {
    val requestPayload = GetCurrentNodePacket(position)
    MisakaNetworkClient.FUTURE_MANAGER.send(
        requestPayload
    ) { node ->
        if (node == null) return@send
        connectedNodeContainer.clearChildren()
        val nodeName = node.nodeName
        val connectedNodeWidget =
            nodeRow(position, connectedNodeContainer, nodeList, nodeName, isConnected = true, isNone = node.isNone)
        connectedNodeContainer.add("connected_node", connectedNodeWidget) {
            matchParent()
        }
        updateAvailableNodesList(position, nodeName, connectedNodeContainer, nodeList)
    }
}

private fun updateAvailableNodesList(
    position: BlockPos,
    connectedNodeName: String,
    connectedNodeContainer: FrameLayoutWidget,
    nodeList: LinearLayoutWidget
) {
    val requestPayload = GetAvailableNodesPacket(position)
    MisakaNetworkClient.FUTURE_MANAGER.send(
        requestPayload,
        Consumer { response ->
            if (response == null) return@Consumer
            nodeList.clearChildren()
            val availableNodes = response.availableNodeNames
            availableNodes.removeIf { s -> s == connectedNodeName }
            for (name in availableNodes) {
                val nodeViewPanel = nodeRow(
                    position,
                    connectedNodeContainer,
                    nodeList,
                    name,
                    isConnected = false,
                    isNone = false
                )
                nodeList.add("node_$name", nodeViewPanel) {
                    widthMode(SizeMode.MATCH_PARENT)
                    height(LIST_ITEM_HEIGHT)
                }
            }
        })
}

private fun WidgetContainer.iconButton(name: String, icon: Identifier, action: () -> Unit) {
    button(name) {
        lp {
            gravity(Gravity.CENTER)
            size(14f, 14f)
        }
        onClick { action() }
        image(icon, "content") {
            matchParent()
            background = iconStateList(icon)
        }
    }
}

private fun iconStateList(icon: Identifier): StateListDrawable {
    val defaultDrawable = TextureDrawable(icon)
    defaultDrawable.tintColor = 0xFFE6E6E6.toInt()

    val hoveredDrawable = TextureDrawable(icon)
    hoveredDrawable.tintColor = -0x1

    return StateListDrawable().apply {
        setDefault(defaultDrawable)
        addState(Widget.HOVERED, hoveredDrawable)
    }
}

private fun nodeRow(
    position: BlockPos,
    connectedNodeContainer: FrameLayoutWidget,
    nodeList: LinearLayoutWidget,
    nodeName: String,
    isConnected: Boolean,
    isNone: Boolean
): FrameLayoutWidget = standaloneFrame {
    fill(-0x1, "back") {
        matchParent()
        padding(2f)
        alpha = 0.25f
    }
    row("content", spacing = 4f) {
        lp {
            sizeMode(SizeMode.MATCH_PARENT)
            gravity(Gravity.CENTER_VERTICAL)
            paddingHorizontal(4f)
        }
        image(R.textures.gui.icon.icon_node, "icon") {
            lp {
                gravity(Gravity.CENTER)
                size(14f, 14f)
            }
        }
        text(nodeName, "node_name") {
            lp {
                gravity(Gravity.CENTER_VERTICAL)
            }
            gravity = Gravity.BOTTOM
            ellipsize = Ellipsize.MARQUEE
            weight(1f)
        }
        if (!isConnected) {
            val connectAction = { password: String ->
                MisakaNetworkClient.send(
                    ConnectNodePacket(position, nodeName, password)
                )
                updateConnectedNodeDisplay(position, connectedNodeContainer, nodeList)
            }
            val inputBox = textBox(12, "input") {
                lp {
                    gravity(Gravity.CENTER)
                    size(46f, 10f)
                }
                enter { password -> connectAction(password) }
            }
            iconButton("connect", R.textures.gui.icon.icon_unconnected) {
                connectAction(inputBox.text)
            }
        } else if (!isNone) {
            iconButton("disconnect", R.textures.gui.icon.icon_connected) {
                MisakaNetworkClient.send(DisconnectNodePacket(position))
                updateConnectedNodeDisplay(position, connectedNodeContainer, nodeList)
            }
        }
    }
}
