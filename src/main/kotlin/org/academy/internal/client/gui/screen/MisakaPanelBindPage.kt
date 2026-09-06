package org.academy.internal.client.gui.screen

import net.minecraft.network.chat.Component
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.*
import org.academy.api.client.resources.R

internal object MisakaPanelBindPage {
    fun build(host: MisakaPanelHost): LinearLayoutWidget {
        val page = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = MisakaNetworkPanelScreen.BIND_SPACING_MINOR
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            visibility = Widget.Visibility.GONE
            isEnabled = false
        }

        page.addChild("top_bar", host.createSubmenuTopBar(
            Component.translatable("screen.academy.misaka_bind_node").string
        ))
        page.addChild("top_rule", FillWidget(MisakaNetworkPanelScreen.PRIMARY_FOREGROUND).apply {
            alpha = 0.8f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(1f)
                .marginBottom(1f)
        })

        page.addChild("icon", ImageWidget(R.textures.gui.icon.icon_tonode).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .size(16f, 16f)
        })
        page.addChild(
            "connected_label",
            LabelWidget(Component.translatable("screen.academy.misaka_bind_connected").string)
        )

        val connectedNode = host.data.currentNodeName()
        val connectedIsNone = connectedNode.isEmpty()
        val connectedContainer = FrameLayoutWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(MisakaNetworkPanelScreen.LIST_ITEM_HEIGHT)
                .marginTop(MisakaNetworkPanelScreen.BIND_SPACING_MINOR - MisakaNetworkPanelScreen.BIND_SPACING_MAJOR)
                .marginRight(MisakaNetworkPanelScreen.SCROLLBAR_WIDTH + MisakaNetworkPanelScreen.BIND_SPACING_MINOR)
        }
        page.addChild("connected_node_container", connectedContainer)
        connectedContainer.addChild(
            "connected_node",
            host.wirelessStyleNodeRow(
                if (connectedIsNone) {
                    Component.translatable("screen.academy.misaka_bind_none").string
                } else {
                    connectedNode
                },
                isConnected = true,
                isNone = connectedIsNone
            ).apply {
                layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            }
        )

        page.addChild(
            "available_label",
            LabelWidget(Component.translatable("screen.academy.misaka_bind_available").string)
        )

        val listContainer = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = MisakaNetworkPanelScreen.BIND_SPACING_MINOR
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .height(0f)
                .marginTop(MisakaNetworkPanelScreen.BIND_SPACING_MINOR - MisakaNetworkPanelScreen.BIND_SPACING_MAJOR)
        }
        page.addChild("list_container", listContainer)

        val scrollPanel = ScrollPanelWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .width(0f)
                .heightMode(SizeMode.MATCH_PARENT)
        }
        listContainer.addChild("scroll_panel", scrollPanel)
        listContainer.addChild("scroll_bar", ScrollBarWidget(scrollPanel, Orientation.VERTICAL).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(MisakaNetworkPanelScreen.SCROLLBAR_WIDTH)
                .heightMode(SizeMode.MATCH_PARENT)
        })

        val nodeList = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
        }
        scrollPanel.setContent(nodeList)

        for (nodeName in host.data.availableNodes()) {
            if (nodeName == connectedNode) {
                continue
            }
            nodeList.addChild(
                "node_$nodeName",
                host.wirelessStyleNodeRow(nodeName, isConnected = false, isNone = false).apply {
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                        .height(MisakaNetworkPanelScreen.LIST_ITEM_HEIGHT)
                }
            )
        }
        return page
    }

}
