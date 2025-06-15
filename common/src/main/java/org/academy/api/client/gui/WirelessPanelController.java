package org.academy.api.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import org.academy.AcademyCraftClient;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.network.NetworkManagerClient;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.wireless.ConnectNodePacket;
import org.academy.api.common.wireless.DisconnectNodePacket;
import org.academy.api.common.wireless.GetAvailableNodesPacket;
import org.academy.api.common.wireless.GetCurrentNodePacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static org.academy.api.client.renderer.RenderTypes.*;

public class WirelessPanelController {
    public static final float PANEL_WIDTH = 176f;
    public static final float PANEL_HEIGHT = 187;
    public static final String PANEL_WIRELESS_NAME = "panel_wireless";

    private final Screen parentScreen;
    private final PanelWidget screenPanel;
    private final PanelWidget wirelessPanel;
    private final SmoothScrollPanelWidget nodeListPanel;
    private String connectedNodeName = "None";
    private final BlockPos position;
    @Nullable
    private final Consumer<String> onNodeNameChangedCallback;

    public WirelessPanelController(@NotNull Screen parentScreen, BlockPos position, @Nullable Consumer<String> onNodeNameChangedCallback) {
        this.parentScreen = parentScreen;
        this.position = position;
        this.onNodeNameChangedCallback = onNodeNameChangedCallback;
        this.screenPanel = createScreenPanel();
        this.wirelessPanel = screenPanel.getChildUnSafe(PANEL_WIRELESS_NAME);
        this.nodeListPanel = this.wirelessPanel.getChildUnSafe("node_list");
    }

    private PanelWidget createScreenPanel() {
        PanelWidget panel = new PanelWidget(0, 0, 0, 0);
        panel.setZ(100);
        panel.setVisible(false);
        panel.setEnabled(false);

        BackgroundWidget backgroundWidget = new BackgroundWidget(this.parentScreen);
        backgroundWidget.runnable = this::hide;
        panel.addChild("screen_back", backgroundWidget);

        PanelWidget localWirelessPanel = createWirelessPanel();
        panel.addChild(PANEL_WIRELESS_NAME, localWirelessPanel);

        return panel;
    }

    private PanelWidget createWirelessPanel() {
        PanelWidget panel = new PanelWidget(0, 0, PANEL_WIDTH, PANEL_HEIGHT);

        BlendQuadWidget back = new BlendQuadWidget(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
        back.red = 0;
        back.green = 0;
        back.blue = 0;
        back.alpha = 0.5f;
        panel.addChild("back", back);

        ImageWidget icon = new ImageWidget(10, 10, 16, 16, RENDER_TYPE_WIRELESS_PANEL_VIEW_ICON);
        panel.addChild("icon", icon);

        LabelWidget connectedLabel = new LabelWidget("Connected", 12, 30);
        panel.addChild("connected_node_label", connectedLabel);

        LabelWidget availableLabel = new LabelWidget("Available", 12, 54);
        panel.addChild("available_node_label", availableLabel);

        SmoothScrollPanelWidget listPanel = new SmoothScrollPanelWidget(10, 64, 160, 114);
        panel.addChild("node_list", listPanel);

        VerticalScrollBarWidget scrollBar = new VerticalScrollBarWidget(listPanel, 160, 64, 5, 114);
        panel.addChild("scroll_bar", scrollBar);

        return panel;
    }

    public PanelWidget getScreenPanel() {
        return screenPanel;
    }

    public void show() {
        if (screenPanel.getParent() instanceof PanelWidget parent) {
            screenPanel.setWidth(parent.getWidth());
            screenPanel.setHeight(parent.getHeight());
            wirelessPanel.setX((parent.getWidth() - PANEL_WIDTH) / 2);
            wirelessPanel.setY((parent.getHeight() - PANEL_HEIGHT) / 2);
        }
        screenPanel.setVisible(true);
        screenPanel.setEnabled(true);
        requestCurrentNodeStatus();
        requestAvailableNodes();
    }

    public void hide() {
        screenPanel.setVisible(false);
        screenPanel.setEnabled(false);
    }


    private void requestAvailableNodes() {
        GetAvailableNodesPacket requestPayload = new GetAvailableNodesPacket(position);
        AcademyCraftClient.FUTURE_MANAGER_CLIENT_INSTANCE.sendRequestToServer(
                requestPayload,
                (GetAvailableNodesPacket.Response response) -> {
                    if (response != null && response.availableNodeNames != null) {
                        nodeListPanel.clearChildren();
                        response.availableNodeNames.removeIf(s -> s.equals(this.connectedNodeName));
                        for (int i = 0; i < response.availableNodeNames.size(); i++) {
                            String name = response.availableNodeNames.get(i);
                            PanelWidget nodeViewPanel = getNodeWidget(2, i * 18f, name, false, false);
                            nodeListPanel.addChild("node_" + name, nodeViewPanel);
                        }
                    }
                }
        );
    }

    private void requestCurrentNodeStatus() {
        GetCurrentNodePacket requestPayload = new GetCurrentNodePacket(position);
        AcademyCraftClient.FUTURE_MANAGER_CLIENT_INSTANCE.sendRequestToServer(
                requestPayload,
                (GetCurrentNodePacket.Response response) -> {
                    if (response != null) {
                        updateConnectedNodeDisplay(response.isNull, response.nodeName);
                    }
                }
        );
    }

    private void updateConnectedNodeDisplay(boolean isNull, String nodeName) {
        boolean changed = !this.connectedNodeName.equals(nodeName);
        this.connectedNodeName = nodeName;
        this.wirelessPanel.removeChild("connected_node");
        PanelWidget connectedNodeWidgetRef = getNodeWidget(12, 38, nodeName, true, isNull);
        this.wirelessPanel.addChild("connected_node", connectedNodeWidgetRef);

        if (changed) {
            if (onNodeNameChangedCallback != null) {
                onNodeNameChangedCallback.accept(nodeName);
            }
            if (this.wirelessPanel.isVisible()) {
                requestAvailableNodes();
            }
        }
    }

    private @NotNull PanelWidget getNodeWidget(float x, float y, String nodeName, boolean isConnected, boolean isNull) {
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
                    NetworkManagerClient.sendPacket(new C2SPacket(new ConnectNodePacket(position, nodeName, password)));
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
                buttonWidget.defaultHoverEffect = true;
                nodeViewPanel.addChild("button", buttonWidget);
            } else {
                if (!isNull) {
                    ImageButtonWidget buttonWidget = new ImageButtonWidget(118, 1, 14, 14, RENDER_TYPE_ICON_CONNECTED, () ->
                    {
                        NetworkManagerClient.sendPacket(new C2SPacket(new DisconnectNodePacket(position)));
                        requestCurrentNodeStatus();
                        requestAvailableNodes();
                    }
                    );
                    nodeViewPanel.addChild("button", buttonWidget);
                }
            }
        }
        return nodeViewPanel;
    }
}