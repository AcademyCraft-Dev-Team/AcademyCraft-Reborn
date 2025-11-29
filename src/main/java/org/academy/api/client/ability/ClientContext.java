package org.academy.api.client.ability;

import org.academy.api.common.ability.Context;

public abstract class ClientContext implements Context {
    @Override
    public void unregister() {
        AbilitySystemClient.unregisterContext(this);
    }
}