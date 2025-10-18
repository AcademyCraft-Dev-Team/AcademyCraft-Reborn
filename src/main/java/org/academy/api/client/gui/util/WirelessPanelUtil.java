package org.academy.api.client.gui.util;

import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.gui.framework.Orientation;
import org.academy.api.client.gui.widget.*;
import org.academy.api.common.wireless.ConnectNodePacket;
import org.academy.api.common.wireless.DisconnectNodePacket;
import org.academy.api.common.wireless.GetAvailableNodesPacket;
import org.academy.api.common.wireless.GetCurrentNodePacket;
import org.misaka.MisakaNetworkClient;

import java.util.concurrent.atomic.AtomicReference;
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

    public static PanelWidget create(float x, float y, BlockPos position, boolean withBackground) {
        var root = new PanelWidget(x, y, PANEL_WIDTH, PANEL_HEIGHT);

        if (withBackground) {
            var back = new BlendQuadWidget(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
            back.setAlpha(0.5f);
            root.addChild("back", back);
        }

        var content = new PanelWidget(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
        root.addChild("content", content);
        {
            var connectedNodeNameRef = new AtomicReference<>("None");
            var currentY = MARGIN_VERTICAL;

            var icon = new ImageWidget(MARGIN_HORIZONTAL, currentY, 16.0f, 16.0f, ICON_OPEN_WIRELESS_PANEL);
            icon.setZ(1);
            icon.setTextureFilter(FilterMode.LINEAR, true);
            content.addChild("icon", icon);
            currentY += icon.getHeight() + SPACING_MAJOR;

            var connectedLabel = new LabelWidget("Connected", MARGIN_HORIZONTAL, currentY);
            connectedLabel.setZ(1);
            content.addChild("connected_node_label", connectedLabel);
            currentY += connectedLabel.getHeight() + SPACING_MINOR;
            currentY += LIST_ITEM_HEIGHT + SPACING_MINOR;

            var availableLabel = new LabelWidget("Available", MARGIN_HORIZONTAL, currentY);
            availableLabel.setZ(1);
            content.addChild("available_node_label", availableLabel);
            currentY += availableLabel.getHeight() + SPACING_MINOR;

            var listWidth = PANEL_WIDTH - 2 * MARGIN_HORIZONTAL - SCROLLBAR_WIDTH - SPACING_MINOR;
            var listHeight = PANEL_HEIGHT - currentY - MARGIN_VERTICAL;
            var nodeList = new ScrollPanelWidget(MARGIN_HORIZONTAL, currentY, listWidth, listHeight);
            content.addChild("node_list", nodeList);

            var scrollBar = new ScrollBarWidget(nodeList, nodeList.getX() + listWidth + SPACING_MINOR, currentY, SCROLLBAR_WIDTH, listHeight, Orientation.VERTICAL);
            scrollBar.setZ(1.0f);
            content.addChild("scroll_bar", scrollBar);

            updateConnectedNodeDisplay(content, position, connectedNodeNameRef, connectedLabel, nodeList);
        }

        return root;
    }

    private static void updateConnectedNodeDisplay(PanelWidget content, BlockPos position, AtomicReference<String> connectedNodeNameRef, LabelWidget connectedLabel, ScrollPanelWidget nodeList) {
        var requestPayload = new GetCurrentNodePacket(position);
        MisakaNetworkClient.FUTURE_MANAGER.sendRequestToServer(requestPayload,
                (GetCurrentNodePacket.Response response) -> {
                    if (response == null) return;

                    connectedNodeNameRef.set(response.getNodeName());
                    content.removeChild("connected_node");

                    var connectedNodeY = connectedLabel.getY() + connectedLabel.getHeight() + SPACING_MINOR;
                    var connectedNodeWidget = getNodeWidget(content, position, connectedNodeNameRef, connectedLabel, nodeList, response.getNodeName(), true, response.isNull());
                    connectedNodeWidget.setX(MARGIN_HORIZONTAL);
                    connectedNodeWidget.setY(connectedNodeY);
                    content.addChild("connected_node", connectedNodeWidget);
                    NeoForge.EVENT_BUS.post(new ConnectionStatusChangedEvent(response.getNodeName()));

                    updateAvailableNodesList(content, position, connectedNodeNameRef, connectedLabel, nodeList);
                });
    }

    private static void updateAvailableNodesList(PanelWidget content, BlockPos position, AtomicReference<String> connectedNodeNameRef, LabelWidget connectedLabel, ScrollPanelWidget nodeList) {
        var requestPayload = new GetAvailableNodesPacket(position);
        MisakaNetworkClient.FUTURE_MANAGER.sendRequestToServer(requestPayload,
                response -> {
                    if (response == null) return;
                    nodeList.clearChildren();
                    var availableNodes = response.getAvailableNodeNames();
                    availableNodes.removeIf(s -> s.equals(connectedNodeNameRef.get()));

                    for (var i = 0; i < availableNodes.size(); i++) {
                        var name = availableNodes.get(i);
                        var nodeViewPanel = getNodeWidget(content, position, connectedNodeNameRef, connectedLabel, nodeList, name, false, false);
                        nodeViewPanel.setY(i * LIST_ITEM_HEIGHT);
                        nodeList.addChild("node_" + name, nodeViewPanel);
                    }
                });
    }

    private static PanelWidget getNodeWidget(PanelWidget content, BlockPos position, AtomicReference<String> connectedNodeNameRef, LabelWidget connectedLabel, ScrollPanelWidget nodeList, String nodeName, boolean isConnected, boolean isNull) {
        var nodeViewPanel = new PanelWidget(0, 0, 156.0f, 16.0f);
        {
            var nodeBack = new ImageWidget(0, 0, 140.0f, 16.0f, UI_BACKGROUND_LIGHT);
            nodeViewPanel.addChild("back", nodeBack);

            var nodeIcon = new ImageWidget(4.0f, 1.0f, 14.0f, 14.0f, ICON_NODE);
            nodeIcon.setZ(1);
            nodeViewPanel.addChild("icon", nodeIcon);

            var nodeNameLabel = new HoverLabelWidget(nodeName, 22.0f, 4.0f, 44.0f);
            nodeNameLabel.setZ(1);
            nodeViewPanel.addChild("node_name", nodeNameLabel);

            if (!isConnected) {
                Consumer<String> connectAction = password -> {
                    MisakaNetworkClient.sendPacket(new ConnectNodePacket(position, nodeName, password));
                    updateConnectedNodeDisplay(content, position, connectedNodeNameRef, connectedLabel, nodeList);
                };
                var inputBox = new TextBoxWidget(12, 68.0f, 3.0f, 46.0f, 10.0f);
                inputBox.setWhenEnter(connectAction);
                inputBox.setShowBackground(true);
                nodeViewPanel.addChild("input", inputBox);

                var connectButton = new ImageButtonWidget(118.0f, 1.0f, 14.0f, 14.0f, ICON_UNCONNECTED, () -> connectAction.accept(inputBox.getText()));
                connectButton.setZ(1);
                nodeViewPanel.addChild("button", connectButton);
            } else if (!isNull) {
                var disconnectButton = new ImageButtonWidget(118.0f, 1.0f, 14.0f, 14.0f, ICON_CONNECTED, () -> {
                    MisakaNetworkClient.sendPacket(new DisconnectNodePacket(position));
                    updateConnectedNodeDisplay(content, position, connectedNodeNameRef, connectedLabel, nodeList);
                });
                disconnectButton.setZ(1);
                nodeViewPanel.addChild("button", disconnectButton);
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