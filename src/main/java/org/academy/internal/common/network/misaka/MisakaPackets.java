package org.academy.internal.common.network.misaka;

public final class MisakaPackets {
    private MisakaPackets() {
    }

    public static void initServer() {
        SetMisakaWanderStylePacket.initServer();
        SetMisakaWanderAnchorPacket.initServer();
        SetMisakaNetworkNodePacket.initServer();
        TogglePickUpMisakaPacket.initServer();
        RequestMisakaPanelPacket.initServer();
        MisakaSisterHandInteractPacket.initServer();
        RequestMisakaNetManagePacket.initServer();
        SetMisakaNetworkAllocationPacket.initServer();
        SetMisakaNetworkPermissionPacket.initServer();
        DisconnectMisakaFromNetworkPacket.initServer();
        TransferDeviceOwnerPacket.initServer();
    }

    public static void initClient() {
        MisakaPanelDataPacket.initClient();
        MisakaNetManageDataPacket.initClient();
        MisakaOrbitalChargePacket.initClient();
    }
}
