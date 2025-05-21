package org.academy.api.client.gui;

import net.minecraft.core.BlockPos;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.network.FutureManagerClient;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.wireless.ConnectNodePacket;
import org.academy.api.common.wireless.DisconnectNodePacket;
import org.academy.api.common.wireless.GetAvailableNodesPacket;
import org.academy.api.common.wireless.GetCurrentNodePacket;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

import static org.academy.api.client.resource.TextureResources.RenderTypes.*;

public class WirelessPanelHelper {
    public static final float PANEL_WIDTH = 176f;
    public static final float PANEL_HEIGHT = 187;
    public static final String PANEL_WIRELESS_NAME = "panel_wireless";

    public static PanelWidget getWirelessPanel(float x, float y) {
        PanelWidget wirelessPanel = new PanelWidget(x, y, PANEL_WIDTH, PANEL_HEIGHT);
        {
            ImageWidget back = new ImageWidget(0, 0, PANEL_WIDTH, PANEL_HEIGHT, RENDER_TYPE_ELEMENT_BACK_DARK);
            wirelessPanel.addChild("back", back);

            ImageWidget icon = new ImageWidget(10, 10, 16, 16, RENDER_TYPE_WIRELESS_PANEL_VIEW_ICON);
            wirelessPanel.addChild("icon", icon);

            LabelWidget connectedLabel = new LabelWidget("Connected", 12, 30);
            wirelessPanel.addChild("connected_node_label", connectedLabel);

            LabelWidget availableLabel = new LabelWidget("Available", 12, 54);
            wirelessPanel.addChild("available_node_label", availableLabel);

            SmoothScrollPanelWidget nodeListPanel = new SmoothScrollPanelWidget(10, 64, 160, 114);
            wirelessPanel.addChild("node_list", nodeListPanel);

            VerticalScrollBarWidget scrollBar = new VerticalScrollBarWidget(nodeListPanel, 160, 64, 5, 114);
            wirelessPanel.addChild("scroll_bar", scrollBar);
        }
        return wirelessPanel;
    }

    public interface WirelessPanel {
        SmoothScrollPanelWidget getNodeList();

        PanelWidget getWirelessPanel();

        String getConnectedNodeName();

        void setConnectedNodeName(String connectedNodeName);

        BlockPos getPosition();

        default void requestAvailableNodes(SmoothScrollPanelWidget listPanel) {
            FutureManagerClient.sendFutureRequestToServer(GetAvailableNodesPacket.class, (List<String> availableNodeNames) -> {
                listPanel.clearChildren();
                availableNodeNames.removeIf(s -> s.equals(getConnectedNodeName()));
                for (int i = 0; i < availableNodeNames.size(); i++) {
                    String name = availableNodeNames.get(i);
                    PanelWidget nodeViewPanel = getNodeWidget(2, i * 18f, name, false, false);
                    listPanel.addChild("node_" + name, nodeViewPanel);
                }
            }, getPosition());
        }

        default @NotNull PanelWidget getNodeWidget(float x, float y, String nodeName, boolean isConnected, boolean isNull) {
            PanelWidget nodeViewPanel = new PanelWidget(x, y, 158, 16);
            {
                ImageWidget nodeBack = new ImageWidget(0, 0, 144, 16, RENDER_TYPE_ELEMENT_BACK_LIGHT);
                nodeViewPanel.addChild("back", nodeBack);
                ImageWidget nodeIcon = new ImageWidget(8, 1, 14, 14, RENDER_TYPE_ICON_NODE);
                nodeViewPanel.addChild("icon", nodeIcon);
                HoverLabelWidget nodeNameLabel = new HoverLabelWidget(nodeName, 24, 3.5f, 40);
                nodeViewPanel.addChild("node_name", nodeNameLabel);
                if (!isConnected) {
                    Consumer<String> connect = password -> {
                        NetworkSystemClient.sendPacket(new C2SPacket(new ConnectNodePacket(getPosition(), nodeName, password)));
                        requestCurrentNodeStatus();
                    };
                    TextBoxWidget inputBox = new TextBoxWidget(12, 70, 3, 46, 10);
                    inputBox.whenEnter = connect;
                    inputBox.showBackground = true;
                    nodeViewPanel.addChild("input", inputBox);
                    ImageButtonWidget buttonWidget = new ImageButtonWidget(118, 1, 14, 14, RENDER_TYPE_ICON_UNCONNECTED, () -> {
                        String password = inputBox.getText();
                        connect.accept(password);
                    });
                    nodeViewPanel.addChild("button", buttonWidget);
                } else {
                    if (!isNull) {
                        ImageButtonWidget buttonWidget = new ImageButtonWidget(118, 1, 14, 14, RENDER_TYPE_ICON_CONNECTED, () ->
                        {
                            NetworkSystemClient.sendPacket(new C2SPacket(new DisconnectNodePacket(getPosition())));
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
            FutureManagerClient.sendFutureRequestToServer(GetCurrentNodePacket.class, (Pair<Boolean, String> pair) ->
                    updateConnectedNodeDisplay(pair.getLeft(), pair.getRight()), getPosition());
        }

        default void updateConnectedNodeDisplay(boolean isNull, String nodeName) {
            boolean changed = !getConnectedNodeName().equals(nodeName);
            setConnectedNodeName(nodeName);
            getWirelessPanel().removeChild("connected_node");
            PanelWidget connectedNodeWidgetRef = getNodeWidget(12, 38, nodeName, true, isNull);
            getWirelessPanel().addChild("connected_node", connectedNodeWidgetRef);
            if (changed) {
                if (getWirelessPanel().isVisible()) {
                    SmoothScrollPanelWidget nodeListPanel = getNodeList();
                    requestAvailableNodes(nodeListPanel);
                }
            }
        }
    }

    private WirelessPanelHelper() {
    }
}