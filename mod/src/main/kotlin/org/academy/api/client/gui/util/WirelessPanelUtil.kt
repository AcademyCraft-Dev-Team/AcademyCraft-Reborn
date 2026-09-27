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
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.LinearLayoutWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.api.client.resources.R
import org.academy.api.common.wireless.ConnectNodePacket
import org.academy.api.common.wireless.DisconnectNodePacket
import org.academy.api.common.wireless.GetAvailableNodesPacket
import org.academy.api.common.wireless.GetCurrentNodePacket
import org.misaka.MisakaNetworkClient
import java.util.function.Consumer

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
            size(R.ui.wireless_panel.panel_width, R.ui.wireless_panel.panel_height)
        }

        if (withBackground) {
            blendQuad("back") {
                lp {
                    matchParent()
                }

                alpha = 0.5f
            }
        }

        column("content", spacing = R.ui.wireless_panel.spacing_minor) {
            lp {
                sizeMode(SizeMode.MATCH_PARENT)
                padding(R.ui.wireless_panel.margin_horizontal, R.ui.wireless_panel.margin_vertical)
            }

            image(R.textures.gui.icon.icon_tonode, "icon") {
                lp {
                    size(16f, 16f)
                }
            }

            text("Connected", "connected_node_label")

            connectedNodeContainer = frame("connected_node_container") {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    height(R.ui.wireless_panel.list_item_height)
                    marginTop(R.ui.wireless_panel.spacing_minor - R.ui.wireless_panel.spacing_major)
                    marginRight(R.ui.wireless_panel.scrollbar_width + R.ui.wireless_panel.spacing_minor)
                }
            }

            text("Available", "available_node_label")

            row("list_container", spacing = R.ui.wireless_panel.spacing_minor) {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    height(0f)
                    marginTop(R.ui.wireless_panel.spacing_minor - R.ui.wireless_panel.spacing_major)
                }

                weight(1f)

                val scroll = scrollPanel(Orientation.VERTICAL, "scroll_panel") {
                    lp {
                        width(0f)
                        heightMode(SizeMode.MATCH_PARENT)
                    }

                    weight(1f)
                }

                nodeList = standaloneColumn {
                    lp {
                        sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                    }

                    this.name = "node_list"
                }

                scroll.setContent(nodeList)

                scrollBar(scroll, Orientation.VERTICAL, "scroll_bar") {
                    lp {
                        width(R.ui.wireless_panel.scrollbar_width)
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
    MisakaNetworkClient.FUTURE_MANAGER.send(requestPayload) { node ->
        if (node == null) return@send

        connectedNodeContainer.clearChildren()

        val nodeName = node.nodeName
        val connectedNodeWidget = nodeRow(
            position,
            connectedNodeContainer,
            nodeList,
            nodeName,
            isConnected = true,
            isNone = node.isNone
        )

        connectedNodeContainer.add("connected_node", connectedNodeWidget) {
            lp {
                matchParent()
            }
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
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(R.ui.wireless_panel.list_item_height)
                    }
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
            lp {
                matchParent()
            }

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
        lp {
            matchParent()
            padding(2f)
        }

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
                MisakaNetworkClient.send(ConnectNodePacket(position, nodeName, password))
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
