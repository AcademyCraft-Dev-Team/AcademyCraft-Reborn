package org.academy.api.client.network;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.academy.api.common.network.AbstractNetworkManager;
import org.academy.api.common.network.NetworkSystem;

public class NetworkManagerClient extends AbstractNetworkManager {
    public static Connection connection;

    public NetworkManagerClient(NetworkSystem networkSystem) {
        super(networkSystem);
    }

    public static void sendPacket(Packet<?> packet) {
        if (connection != null) {
            connection.send(packet);
        }
    }
}