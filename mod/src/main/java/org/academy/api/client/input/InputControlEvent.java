package org.academy.api.client.input;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class InputControlEvent extends Event implements ICancellableEvent {
    @Override
    public void setCanceled(boolean canceled) {
        if (canceled && TemporalInputAccess.isLocalExternallyImmune()
                && !UiInputContext.isActive() && TemporalInputAccess.isExternalControl()) return;
        ICancellableEvent.super.setCanceled(canceled);
    }
}
