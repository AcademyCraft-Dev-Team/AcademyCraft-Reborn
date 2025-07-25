package org.academy.api.server.network;

import org.academy.api.common.network.AbstractNetworkManager;
import org.academy.api.common.network.NetworkSystem;

public class NetworkManagerServer extends AbstractNetworkManager {
    public NetworkManagerServer(NetworkSystem networkSystem) {
        super(networkSystem);
    }
}