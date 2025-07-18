package org.academy.api.client.gui.widget;

import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.Event;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.api.client.gui.framework.Orientation;
import org.academy.api.client.gui.framework.Widget;
import org.academy.api.client.network.NetworkManagerClient;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.wireless.ConnectNodePacket;
import org.academy.api.common.wireless.DisconnectNodePacket;
import org.academy.api.common.wireless.GetAvailableNodesPacket;
import org.academy.api.common.wireless.GetCurrentNodePacket;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

import static org.academy.api.client.render.RenderTypes.*;

public class WirelessPanelWidget extends PanelWidget {
    public static final float PANEL_WIDTH = 176f;
    public static final float PANEL_HEIGHT = 187;

    private final BlockPos position;
    private final ScrollPanelWidget nodeList;
    private String connectedNodeName = "None";

    public WirelessPanelWidget(float x, float y, @NotNull BlockPos position) {
        super(x, y, PANEL_WIDTH, PANEL_HEIGHT);
        this.position = position;

        var back = new BlendQuadWidget(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
        back.setAlpha(0.5f);
        addChild("back", back);

        var icon = new ImageWidget(10, 10, 16, 16, WIRELESS_PANEL_VIEW_ICON);
        addChild("icon", icon);

        var connectedLabel = new LabelWidget("Connected", 12, 30);
        addChild("connected_node_label", connectedLabel);

        var availableLabel = new LabelWidget("Available", 12, 54);
        addChild("available_node_label", availableLabel);

        nodeList = new ScrollPanelWidget(10, 64, 160, 114);
        addChild("node_list", nodeList);

        var scrollBar = new ScrollBarWidget(nodeList, 160, 64, 5, 114, Orientation.VERTICAL);
        scrollBar.setZ(scrollBar.getZ() + 1);
        addChild("scroll_bar", scrollBar);

        requestCurrentNodeStatus();
    }

    @Override
    public Widget setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            requestCurrentNodeStatus();
        }
        return this;
    }

    private void requestAvailableNodes() {
        var requestPayload = new GetAvailableNodesPacket(position);
        AcademyCraftClient.CLIENT_FUTURE_MANAGER.sendRequestToServer(
                requestPayload,
                (GetAvailableNodesPacket.Response response) -> {
                    if (response != null && response.availableNodeNames != null) {
                        nodeList.clearChildren();
                        response.availableNodeNames.removeIf(s -> s.equals(this.connectedNodeName));
                        for (int i = 0; i < response.availableNodeNames.size(); i++) {
                            var name = response.availableNodeNames.get(i);
                            var nodeViewPanel = getNodeWidget(2, i * 18f, name, false, false);
                            nodeList.addChild("node_" + name, nodeViewPanel);
                        }
                    }
                }
        );
    }

    private @NotNull PanelWidget getNodeWidget(float x, float y, String nodeName, boolean isConnected, boolean isNull) {
        var nodeViewPanel = new PanelWidget(x, y, 158, 16);
        var nodeBack = new ImageWidget(0, 0, 144, 16, ELEMENT_BACK_LIGHT);
        nodeViewPanel.addChild("back", nodeBack);
        var nodeIcon = new ImageWidget(8, 1, 14, 14, ICON_NODE);
        nodeViewPanel.addChild("icon", nodeIcon);
        var nodeNameLabel = new HoverLabelWidget(nodeName, 24, 3.5f, 40);
        nodeViewPanel.addChild("node_name", nodeNameLabel);

        if (!isConnected) {
            Consumer<String> connect = password -> {
                NetworkManagerClient.sendPacket(new C2SPacket(new ConnectNodePacket(this.position, nodeName, password)));
                this.requestCurrentNodeStatus();
            };
            var inputBox = new TextBoxWidget(12, 70, 3, 46, 10);
            inputBox.whenEnter = connect;
            inputBox.showBackground = true;
            nodeViewPanel.addChild("input", inputBox);
            var buttonWidget = new ImageButtonWidget(118, 1, 14, 14, ICON_UNCONNECTED, () -> {
                var password = inputBox.getText();
                connect.accept(password);
            });
            buttonWidget.defaultHoverEffect = true;
            nodeViewPanel.addChild("button", buttonWidget);
        } else {
            if (!isNull) {
                var buttonWidget = new ImageButtonWidget(118, 1, 14, 14, ICON_CONNECTED, () ->
                {
                    NetworkManagerClient.sendPacket(new C2SPacket(new DisconnectNodePacket(position)));
                    requestCurrentNodeStatus();
                });
                nodeViewPanel.addChild("button", buttonWidget);
            }
        }
        return nodeViewPanel;
    }

    private void requestCurrentNodeStatus() {
        var requestPayload = new GetCurrentNodePacket(position);
        AcademyCraftClient.CLIENT_FUTURE_MANAGER.sendRequestToServer(
                requestPayload,
                (GetCurrentNodePacket.Response response) -> {
                    if (response != null) {
                        updateConnectedNodeDisplay(response.isNull, response.nodeName);
                    }
                }
        );
    }

    private void updateConnectedNodeDisplay(boolean isNull, String nodeName) {
        boolean changed = !connectedNodeName.equals(nodeName);
        connectedNodeName = nodeName;
        removeChild("connected_node");
        var connectedNodeWidgetRef = getNodeWidget(12, 38, nodeName, true, isNull);
        addChild("connected_node", connectedNodeWidgetRef);
        AcademyCraft.EVENT_BUS.post(new ConnectionStatusChangedEvent(nodeName));

        if (changed && isVisible()) {
            requestAvailableNodes();
        }
    }

    public static class ConnectionStatusChangedEvent extends Event {
        public final String nodeName;

        public ConnectionStatusChangedEvent(String nodeName) {
            this.nodeName = nodeName;
        }
    }
}