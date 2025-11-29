package org.academy.api.client.gui.util;

import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.Orientation;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.widget.*;
import org.academy.api.common.wireless.ConnectNodePacket;
import org.academy.api.common.wireless.DisconnectNodePacket;
import org.academy.api.common.wireless.GetAvailableNodesPacket;
import org.academy.api.common.wireless.GetCurrentNodePacket;
import org.misaka.MisakaNetworkClient;

import java.util.function.Consumer;

import static org.academy.api.client.Resource.Textures.*;

public final class WirelessPanelUtil {
    public static final float PANEL_WIDTH = 176.0f;
    public static final float PANEL_HEIGHT = 187.0f;

    private static final float MARGIN_HORIZONTAL = 12.0f;
    private static final float MARGIN_VERTICAL = 10.0f;
    private static final float SPACING_MAJOR = 8.0f;
    private static final float SPACING_MINOR = 4.0f;
    private static final float LIST_ITEM_HEIGHT = 18.0f;
    private static final float SCROLLBAR_WIDTH = 5.0f;

    private WirelessPanelUtil() {
    }

    public static FrameLayoutWidget create(BlockPos position, boolean withBackground) {
        var root = new FrameLayoutWidget();
        root.setLayoutParams(
                new WidgetContainer.LayoutParams()
                        .size(PANEL_WIDTH, PANEL_HEIGHT)
        );
        {
            if (withBackground) {
                var back = new BlendQuadWidget();
                back.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT));
                back.setAlpha(0.5f);
                root.addChild("back", back);
            }

            var content = new LinearLayoutWidget();
            content.setOrientation(Orientation.VERTICAL);
            content.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                    .padding(MARGIN_HORIZONTAL, MARGIN_VERTICAL)
            );
            content.setSpacing(SPACING_MINOR);
            root.addChild("content", content);
            {
                var icon = new ImageWidget(ICON_OPEN_WIRELESS_PANEL);
                icon.setLayoutParams(
                        new LinearLayoutWidget.LayoutParams()
                                .size(16, 16)
                );
                content.addChild("icon", icon);

                var connectedLabel = new LabelWidget("Connected");
                content.addChild("connected_node_label", connectedLabel);

                var connectedNodeContainer = new FrameLayoutWidget();
                connectedNodeContainer.setLayoutParams(new LinearLayoutWidget.LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                        .height(LIST_ITEM_HEIGHT)
                        .marginTop(SPACING_MINOR - SPACING_MAJOR)
                );
                content.addChild("connected_node_container", connectedNodeContainer);

                var availableLabel = new LabelWidget("Available");
                content.addChild("available_node_label", availableLabel);

                var listContainer = new LinearLayoutWidget();
                listContainer.setOrientation(Orientation.HORIZONTAL);
                listContainer.setSpacing(SPACING_MINOR);
                listContainer.setLayoutParams(new LinearLayoutWidget.LayoutParams()
                        .weight(1)
                        .widthMode(SizeMode.MATCH_PARENT)
                        .height(0)
                        .marginTop(SPACING_MINOR - SPACING_MAJOR)
                );
                content.addChild("list_container", listContainer);
                {
                    var scrollPanel = new ScrollPanelWidget();
                    scrollPanel.setLayoutParams(new LinearLayoutWidget.LayoutParams()
                            .weight(1)
                            .width(0)
                            .heightMode(SizeMode.MATCH_PARENT)
                    );
                    listContainer.addChild("scroll_panel", scrollPanel);

                    var scrollBar = new ScrollBarWidget(scrollPanel, Orientation.VERTICAL);
                    scrollBar.setLayoutParams(new LinearLayoutWidget.LayoutParams()
                            .width(SCROLLBAR_WIDTH)
                            .heightMode(SizeMode.MATCH_PARENT)
                    );
                    listContainer.addChild("scroll_bar", scrollBar);

                    var nodeList = new LinearLayoutWidget();
                    nodeList.setOrientation(Orientation.VERTICAL);
                    nodeList.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                    );
                    scrollPanel.addChild("node_list", nodeList);

                    updateConnectedNodeDisplay(position, connectedNodeContainer, nodeList);
                }
            }
        }
        return root;
    }

    private static void updateConnectedNodeDisplay(BlockPos position, FrameLayoutWidget connectedNodeContainer, LinearLayoutWidget nodeList) {
        var requestPayload = new GetCurrentNodePacket(position);
        MisakaNetworkClient.FUTURE_MANAGER.sendRequestToServer(requestPayload,
                (GetCurrentNodePacket.Response response) -> {
                    if (response == null) return;

                    connectedNodeContainer.clearChildren();
                    var nodeName = response.getNodeName();
                    var connectedNodeWidget = getNodeWidget(position, connectedNodeContainer, nodeList, nodeName, true, response.isNull());
                    connectedNodeWidget.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT)
                    );
                    connectedNodeContainer.addChild("connected_node", connectedNodeWidget);
                    NeoForge.EVENT_BUS.post(new ConnectionStatusChangedEvent(nodeName));

                    updateAvailableNodesList(position, nodeName, connectedNodeContainer, nodeList);
                });
    }

    private static void updateAvailableNodesList(BlockPos position, String connectedNodeName, FrameLayoutWidget connectedNodeContainer, LinearLayoutWidget nodeList) {
        var requestPayload = new GetAvailableNodesPacket(position);
        MisakaNetworkClient.FUTURE_MANAGER.sendRequestToServer(requestPayload,
                response -> {
                    if (response == null) return;
                    nodeList.clearChildren();
                    var availableNodes = response.getAvailableNodeNames();
                    availableNodes.removeIf(s -> s.equals(connectedNodeName));

                    for (var name : availableNodes) {
                        var nodeViewPanel = getNodeWidget(position, connectedNodeContainer, nodeList, name, false, false);
                        nodeViewPanel.setLayoutParams(new LinearLayoutWidget.LayoutParams()
                                .widthMode(SizeMode.MATCH_PARENT)
                                .height(LIST_ITEM_HEIGHT)
                        );
                        nodeList.addChild("node_" + name, nodeViewPanel);
                    }
                });
    }

    private static FrameLayoutWidget getNodeWidget(BlockPos position, FrameLayoutWidget connectedNodeContainer, LinearLayoutWidget nodeList, String nodeName, boolean isConnected, boolean isNull) {
        var nodeViewPanel = new FrameLayoutWidget();
        {
            var nodeBack = new FillWidget(0xFFFFFFFF);
            nodeBack.setLayoutParams(
                    new FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT)
                            .padding(2, 2)
            );
            nodeBack.setAlpha(0.25f);
            nodeViewPanel.addChild("back", nodeBack);

            var itemContent = new LinearLayoutWidget();
            itemContent.setOrientation(Orientation.HORIZONTAL);
            itemContent.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                    .gravity(Gravity.CENTER_VERTICAL)
                    .paddingHorizontal(4)
            );
            itemContent.setSpacing(4);
            nodeViewPanel.addChild("content", itemContent);
            {
                var nodeIcon = new ImageWidget(ICON_NODE);
                nodeIcon.setLayoutParams(new LinearLayoutWidget.LayoutParams()
                        .gravity(Gravity.CENTER)
                        .size(14, 14));
                itemContent.addChild("icon", nodeIcon);

                var nodeNameLabel = new HoverLabelWidget(nodeName);
                nodeNameLabel.setLayoutParams(new LinearLayoutWidget.LayoutParams()
                        .weight(1)
                        .sizeMode(SizeMode.MATCH_PARENT)
                        .gravity(Gravity.CENTER_VERTICAL)
                );
                itemContent.addChild("node_name", nodeNameLabel);

                if (!isConnected) {
                    Consumer<String> connectAction = password -> {
                        MisakaNetworkClient.sendPacket(new ConnectNodePacket(position, nodeName, password));
                        updateConnectedNodeDisplay(position, connectedNodeContainer, nodeList);
                    };
                    var inputBox = new TextBoxWidget(12);
                    inputBox.setLayoutParams(
                            new LinearLayoutWidget.LayoutParams()
                                    .gravity(Gravity.CENTER)
                                    .size(46, 10)
                    );
                    inputBox.setWhenEnter(connectAction);
                    inputBox.setShowBackground(true);
                    itemContent.addChild("input", inputBox);

                    var connectButton = new ImageButtonWidget(ICON_UNCONNECTED, source -> connectAction.accept(inputBox.getText()));
                    connectButton.setLayoutParams(
                            new LinearLayoutWidget.LayoutParams()
                                    .gravity(Gravity.CENTER)
                                    .size(14, 14)
                    );
                    itemContent.addChild("button", connectButton);
                } else if (!isNull) {
                    var disconnectButton = new ImageButtonWidget(ICON_CONNECTED, source -> {
                        MisakaNetworkClient.sendPacket(new DisconnectNodePacket(position));
                        updateConnectedNodeDisplay(position, connectedNodeContainer, nodeList);
                    });
                    disconnectButton.setLayoutParams(
                            new LinearLayoutWidget.LayoutParams()
                                    .gravity(Gravity.CENTER)
                                    .size(14, 14)
                    );
                    itemContent.addChild("button", disconnectButton);
                }
            }
        }
        return nodeViewPanel;
    }

    public static class ConnectionStatusChangedEvent extends Event {
        public final String nodeName;

        public ConnectionStatusChangedEvent(String nodeName) {
            this.nodeName = nodeName;
        }
    }
}