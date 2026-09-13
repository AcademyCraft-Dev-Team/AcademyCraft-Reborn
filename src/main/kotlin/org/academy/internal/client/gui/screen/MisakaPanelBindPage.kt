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

        val connectedNode = host.data.currentNodeName()
        val connectedIsNone = connectedNode.isEmpty()

        page.addChild(
            "body",
            MisakaPanelLayouts.scrollBody(
                spacing = MisakaNetworkPanelScreen.BIND_SPACING_MINOR
            ) {
                addChild("icon", ImageWidget(R.textures.gui.icon.icon_tonode).apply {
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .size(16f, 16f)
                })
                addChild(
                    "connected_label",
                    LabelWidget(Component.translatable("screen.academy.misaka_bind_connected").string)
                )
                addChild(
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
                        layoutParams = LinearLayoutWidget.LayoutParams()
                            .widthMode(SizeMode.MATCH_PARENT)
                            .height(MisakaNetworkPanelScreen.LIST_ITEM_HEIGHT)
                    }
                )
                addChild(
                    "available_label",
                    LabelWidget(Component.translatable("screen.academy.misaka_bind_available").string)
                )
                for (nodeName in host.data.availableNodes()) {
                    if (nodeName == connectedNode) {
                        continue
                    }
                    addChild(
                        "node_$nodeName",
                        host.wirelessStyleNodeRow(nodeName, isConnected = false, isNone = false).apply {
                            layoutParams = LinearLayoutWidget.LayoutParams()
                                .widthMode(SizeMode.MATCH_PARENT)
                                .height(MisakaNetworkPanelScreen.LIST_ITEM_HEIGHT)
                        }
                    )
                }
            }
        )
        return page
    }
}
