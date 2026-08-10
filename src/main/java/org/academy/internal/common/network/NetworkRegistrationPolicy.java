package org.academy.internal.common.network;

import org.misaka.api.common.network.NetworkManager;

/** Keeps static Misaka packet listeners idempotent across integrated-server restarts. */
public final class NetworkRegistrationPolicy {
    private NetworkRegistrationPolicy() {
    }

    public static void replaceStaticRegistration(NetworkManager manager, Class<?> listenerClass) {
        manager.unregister(listenerClass);
    }
}
