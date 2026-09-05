package org.academy.internal.common.network.misaka;

public final class MisakaPackets {
    private MisakaPackets() {
    }

    public static void initServer() {
        SetMisakaWanderStylePacket.initServer();
        SetMisakaNetworkNodePacket.initServer();
        TogglePickUpMisakaPacket.initServer();
        RequestMisakaPanelPacket.initServer();
    }

    public static void initClient() {
        MisakaPanelDataPacket.initClient();
    }
}
