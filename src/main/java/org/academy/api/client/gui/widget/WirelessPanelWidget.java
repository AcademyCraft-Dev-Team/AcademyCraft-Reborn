package org.academy.api.client.gui.widget;

import static org.academy.api.client.Resource.Textures.ICON_CONNECTED;
import static org.academy.api.client.Resource.Textures.ICON_NODE;
import static org.academy.api.client.Resource.Textures.ICON_OPEN_WIRELESS_PANEL;
import static org.academy.api.client.Resource.Textures.ICON_UNCONNECTED;
import static org.academy.api.client.Resource.Textures.UI_BACKGROUND_LIGHT;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraftClient;
import org.academy.api.client.gui.framework.Orientation;
import org.academy.api.client.gui.framework.Widget;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.wireless.ConnectNodePacket;
import org.academy.api.common.wireless.DisconnectNodePacket;
import org.academy.api.common.wireless.GetAvailableNodesPacket;
import org.academy.api.common.wireless.GetCurrentNodePacket;

public class WirelessPanelWidget extends PanelWidget {
    public static final float PANEL_WIDTH = 176.0f;
    public static final float PANEL_HEIGHT = 187.0f;

    private static final float MARGIN_HORIZONTAL = 12.0f;
    private static final float MARGIN_VERTICAL = 10.0f;
    private static final float SPACING_MAJOR = 8.0f;
    private static final float SPACING_MINOR = 4.0f;
    private static final float LIST_ITEM_HEIGHT = 18.0f;
    private static final float SCROLLBAR_WIDTH = 5.0f;

    private final BlockPos position;
    private final ScrollPanelWidget nodeList;
    private String connectedNodeName = "None";

    public WirelessPanelWidget(float x, float y, BlockPos position) {
        super(x, y, PANEL_WIDTH, PANEL_HEIGHT);
        this.position = position;

        var currentY = MARGIN_VERTICAL;

        var back = new BlendQuadWidget(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
        back.setAlpha(0.5f);
        addChild("back", back);

        var icon = new ImageWidget(MARGIN_HORIZONTAL, currentY, 16.0f, 16.0f, ICON_OPEN_WIRELESS_PANEL);
        icon.setZ(1);
        addChild("icon", icon);

        currentY += icon.getHeight() + SPACING_MAJOR;

        var connectedLabel = new LabelWidget("Connected", MARGIN_HORIZONTAL, currentY);
        connectedLabel.setZ(1);
        addChild("connected_node_label", connectedLabel);
        currentY += connectedLabel.getHeight() + SPACING_MINOR;
        currentY += LIST_ITEM_HEIGHT + SPACING_MINOR;

        var availableLabel = new LabelWidget("Available", MARGIN_HORIZONTAL, currentY);
        availableLabel.setZ(1);
        addChild("available_node_label", availableLabel);
        currentY += availableLabel.getHeight() + SPACING_MINOR;

        var listWidth = PANEL_WIDTH - 2 * MARGIN_HORIZONTAL - SCROLLBAR_WIDTH - SPACING_MINOR;
        var listHeight = PANEL_HEIGHT - currentY - MARGIN_VERTICAL;
        nodeList = new ScrollPanelWidget(MARGIN_HORIZONTAL, currentY, listWidth, listHeight);
        addChild("node_list", nodeList);

        var scrollBar = new ScrollBarWidget(nodeList, nodeList.getX() + listWidth + SPACING_MINOR, currentY, SCROLLBAR_WIDTH, listHeight, Orientation.VERTICAL);
        scrollBar.setZ(1.0f);
        addChild("scroll_bar", scrollBar);

        requestCurrentNodeStatus();
    }

    @Override
    public Widget setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible)
            requestCurrentNodeStatus();

        return this;
    }

    private void requestAvailableNodes() {
        var requestPayload = new GetAvailableNodesPacket(position);
        AcademyCraftClient.CLIENT_FUTURE_MANAGER.sendRequestToServer(
                requestPayload,
               response -> {
                    if (response != null && response.getAvailableNodeNames() != null) {
                        nodeList.clearChildren();
                        response.getAvailableNodeNames().removeIf(s -> s.equals(connectedNodeName));
                        for (int i = 0; i < response.getAvailableNodeNames().size(); i++) {
                            var name = response.getAvailableNodeNames().get(i);
                            var nodeViewPanel = getNodeWidget(0, i * LIST_ITEM_HEIGHT, name, false, false);
                            nodeList.addChild("node_" + name, nodeViewPanel);
                        }
                    }
                }
        );
    }

    private PanelWidget getNodeWidget(float x, float y, String nodeName, boolean isConnected, boolean isNull) {
        var nodeViewPanel = new PanelWidget(x, y, 156.0f, 16.0f);

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
                AcademyCraftClient.sendPacket(new C2SPacket(new ConnectNodePacket(position, nodeName, password)));
                requestCurrentNodeStatus();
            };
            var inputBox = new TextBoxWidget(12, 68.0f, 3.0f, 46.0f, 10.0f);
            inputBox.whenEnter = connectAction;
            inputBox.showBackground = true;
            nodeViewPanel.addChild("input", inputBox);

            var buttonWidget = new ImageButtonWidget(118.0f, 1.0f, 14.0f, 14.0f, ICON_UNCONNECTED, () -> {
                var password = inputBox.getText();
                connectAction.accept(password);
            });
            buttonWidget.setZ(1);
            nodeViewPanel.addChild("button", buttonWidget);
        } else if (!isNull) {
            var buttonWidget = new ImageButtonWidget(118.0f, 1.0f, 14.0f, 14.0f, ICON_CONNECTED, () -> {
                AcademyCraftClient.sendPacket(new C2SPacket(new DisconnectNodePacket(position)));
                requestCurrentNodeStatus();
            });
            nodeViewPanel.addChild("button", buttonWidget);
        }
        return nodeViewPanel;
    }

    private void requestCurrentNodeStatus() {
        var requestPayload = new GetCurrentNodePacket(position);
        AcademyCraftClient.CLIENT_FUTURE_MANAGER.sendRequestToServer(
                requestPayload,
                (GetCurrentNodePacket.Response response) -> {
                    if (response != null)
                        updateConnectedNodeDisplay(response.isNull(), response.getNodeName());
                }
        );
    }

    private void updateConnectedNodeDisplay(boolean isNull, String nodeName) {
        connectedNodeName = nodeName;
        removeChild("connected_node");
        var connectedNodeY = getChildUnSafe("connected_node_label").getY() + getChildUnSafe("connected_node_label").getHeight() + SPACING_MINOR;
        var connectedNodeWidgetRef = getNodeWidget(MARGIN_HORIZONTAL, connectedNodeY, nodeName, true, isNull);
        addChild("connected_node", connectedNodeWidgetRef);
        NeoForge.EVENT_BUS.post(new ConnectionStatusChangedEvent(nodeName));

        if (isVisible()) requestAvailableNodes();
    }

    public static class ConnectionStatusChangedEvent extends Event {
        public final String nodeName;

        public ConnectionStatusChangedEvent(String nodeName) {
            this.nodeName = nodeName;
        }
    }
}