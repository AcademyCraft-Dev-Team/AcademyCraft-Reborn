package org.academy.api.client.gui;

import net.minecraft.core.BlockPos;
import org.academy.AcademyCraftClient;
import org.academy.api.client.gui.framework.Orientation;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.network.NetworkManagerClient;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.wireless.ConnectNodePacket;
import org.academy.api.common.wireless.DisconnectNodePacket;
import org.academy.api.common.wireless.GetAvailableNodesPacket;
import org.academy.api.common.wireless.GetCurrentNodePacket;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

import static org.academy.api.client.renderer.RenderTypes.*;

public final class WirelessPanelHelper {
    public static final float PANEL_WIDTH = 176f;
    public static final float PANEL_HEIGHT = 187;
    public static final String PANEL_WIRELESS_NAME = "panel_wireless";

    public static PanelWidget getWirelessPanel(float x, float y) {
        var wirelessPanel = new PanelWidget(x, y, PANEL_WIDTH, PANEL_HEIGHT);
        {
            var back = new BlendQuadWidget(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
            back.setAlpha(0.5f);
            wirelessPanel.addChild("back", back);

            var icon = new ImageWidget(10, 10, 16, 16, RENDER_TYPE_WIRELESS_PANEL_VIEW_ICON);
            wirelessPanel.addChild("icon", icon);

            var connectedLabel = new LabelWidget("Connected", 12, 30);
            wirelessPanel.addChild("connected_node_label", connectedLabel);

            var availableLabel = new LabelWidget("Available", 12, 54);
            wirelessPanel.addChild("available_node_label", availableLabel);

            var nodeListPanel = new ScrollPanelWidget(10, 64, 160, 114);
            wirelessPanel.addChild("node_list", nodeListPanel);

            var scrollBar = new ScrollBarWidget(nodeListPanel, 160, 64, 5, 114, Orientation.VERTICAL);
            scrollBar.setZ(scrollBar.getZ() + 1);
            wirelessPanel.addChild("scroll_bar", scrollBar);
        }
        return wirelessPanel;
    }

    public interface WirelessPanel {
        @NotNull
        ScrollPanelWidget getNodeList();

        @NotNull
        PanelWidget getWirelessPanel();

        @NotNull
        String getConnectedNodeName();

        void setConnectedNodeName(String newConnectedNodeName);

        @NotNull
        BlockPos getPosition();

        default void requestAvailableNodes(ScrollPanelWidget listPanel) {
            var requestPayload = new GetAvailableNodesPacket(getPosition());
            AcademyCraftClient.CLIENT_FUTURE_MANAGER.sendRequestToServer(
                    requestPayload,
                    (GetAvailableNodesPacket.Response response) -> {
                        if (response != null && response.availableNodeNames != null) {
                            listPanel.clearChildren();
                            response.availableNodeNames.removeIf(s -> s.equals(getConnectedNodeName()));
                            for (int i = 0; i < response.availableNodeNames.size(); i++) {
                                var name = response.availableNodeNames.get(i);
                                var nodeViewPanel = getNodeWidget(2, i * 18f, name, false, false);
                                listPanel.addChild("node_" + name, nodeViewPanel);
                            }
                        }
                    }
            );
        }

        default @NotNull PanelWidget getNodeWidget(float x, float y, String nodeName, boolean isConnected, boolean isNull) {
            var nodeViewPanel = new PanelWidget(x, y, 158, 16);
            {
                var nodeBack = new ImageWidget(0, 0, 144, 16, RENDER_TYPE_ELEMENT_BACK_LIGHT);
                nodeViewPanel.addChild("back", nodeBack);
                var nodeIcon = new ImageWidget(8, 1, 14, 14, RENDER_TYPE_ICON_NODE);
                nodeViewPanel.addChild("icon", nodeIcon);
                var nodeNameLabel = new HoverLabelWidget(nodeName, 24, 3.5f, 40);
                nodeViewPanel.addChild("node_name", nodeNameLabel);
                if (!isConnected) {
                    Consumer<String> connect = password -> {
                        NetworkManagerClient.sendPacket(new C2SPacket(new ConnectNodePacket(getPosition(), nodeName, password)));
                        requestCurrentNodeStatus();
                    };
                    var inputBox = new TextBoxWidget(12, 70, 3, 46, 10);
                    inputBox.whenEnter = connect;
                    inputBox.showBackground = true;
                    nodeViewPanel.addChild("input", inputBox);
                    var buttonWidget = new ImageButtonWidget(118, 1, 14, 14, RENDER_TYPE_ICON_UNCONNECTED, () -> {
                        var password = inputBox.getText();
                        connect.accept(password);
                    });
                    buttonWidget.defaultHoverEffect = true;
                    nodeViewPanel.addChild("button", buttonWidget);
                } else {
                    if (!isNull) {
                        var buttonWidget = new ImageButtonWidget(118, 1, 14, 14, RENDER_TYPE_ICON_CONNECTED, () ->
                        {
                            NetworkManagerClient.sendPacket(new C2SPacket(new DisconnectNodePacket(getPosition())));
                            requestCurrentNodeStatus();
                            requestAvailableNodes(getNodeList());
                        }
                        );
                        nodeViewPanel.addChild("button", buttonWidget);
                    }
                }
            }
            return nodeViewPanel;
        }

        default void requestCurrentNodeStatus() {
            var requestPayload = new GetCurrentNodePacket(getPosition());
            AcademyCraftClient.CLIENT_FUTURE_MANAGER.sendRequestToServer(
                    requestPayload,
                    (GetCurrentNodePacket.Response response) -> {
                        if (response != null) {
                            updateConnectedNodeDisplay(response.isNull, response.nodeName);
                        }
                    }
            );
        }

        default void updateConnectedNodeDisplay(boolean isNull, String nodeName) {
            boolean changed = !getConnectedNodeName().equals(nodeName);
            setConnectedNodeName(nodeName);
            getWirelessPanel().removeChild("connected_node");
            var connectedNodeWidgetRef = getNodeWidget(12, 38, nodeName, true, isNull);
            getWirelessPanel().addChild("connected_node", connectedNodeWidgetRef);
            if (changed) {
                if (getWirelessPanel().isVisible()) {
                    var nodeListPanel = getNodeList();
                    requestAvailableNodes(nodeListPanel);
                }
            }
        }
    }

    private WirelessPanelHelper() {
    }
}