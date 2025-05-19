package org.academy.api.client.network;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;

public class NetworkSystemClient {
    public static Connection connection;

    public static void init() {
        FutureManagerClient.register();
    }

    private NetworkSystemClient() {
    }

    public static void sendPacket(Packet<?> packet) {
        if (connection != null) {
            connection.send(packet);
        }
    }
}