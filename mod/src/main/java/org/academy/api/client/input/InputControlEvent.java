package org.academy.api.client.input;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class InputControlEvent extends Event implements ICancellableEvent {
    @Override
    public void setCanceled(boolean canceled) {
        if (canceled && !InputSystem.isUiContext() && TemporalInputAccess.isLocalExternallyImmune()
                && TemporalInputAccess.isExternalControl()) return;
        ICancellableEvent.super.setCanceled(canceled);
    }
}
