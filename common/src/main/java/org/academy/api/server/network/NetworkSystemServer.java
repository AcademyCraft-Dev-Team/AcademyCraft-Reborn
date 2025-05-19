package org.academy.api.server.network;

public final class NetworkSystemServer {
    public static void init() {
        FutureManagerServer.register();
    }

    private NetworkSystemServer() {
    }
}