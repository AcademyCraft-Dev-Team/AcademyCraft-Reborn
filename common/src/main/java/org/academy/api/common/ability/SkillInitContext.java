package org.academy.api.common.ability;

import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.future.FutureManager;

public class SkillInitContext {
    private final NetworkSystem networkSystem;
    private final FutureManager futureManager;

    public SkillInitContext(NetworkSystem networkSystem, FutureManager futureManager) {
        this.networkSystem = networkSystem;
        this.futureManager = futureManager;
    }

    public NetworkSystem getNetworkSystem() {
        return networkSystem;
    }

    public FutureManager getFutureManager() {
        return futureManager;
    }
}