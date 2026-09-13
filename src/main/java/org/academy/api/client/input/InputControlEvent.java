package org.academy.api.client.input;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.academy.internal.client.time.TemporalClientRuntime;
import org.academy.internal.server.time.TemporalControlProvenance;

/** GUI/HUD consumption remains authoritative; unknown gameplay locks are external time control. */
public abstract class InputControlEvent extends Event implements ICancellableEvent {
    @Override
    public void setCanceled(boolean canceled) {
        if (canceled && TemporalClientRuntime.isLocalExternallyImmune()
                && !UiInputContext.isActive() && TemporalControlProvenance.isExternalControl()) return;
        ICancellableEvent.super.setCanceled(canceled);
    }
}
