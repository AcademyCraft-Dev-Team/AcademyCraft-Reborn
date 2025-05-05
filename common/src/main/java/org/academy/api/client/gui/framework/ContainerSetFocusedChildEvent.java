package org.academy.api.client.gui.framework;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.Nullable;

public class ContainerSetFocusedChildEvent extends Event implements ICancellableEvent {
    @Nullable
    public Widget child;

    public ContainerSetFocusedChildEvent(@Nullable Widget child) {
        this.child = child;
    }
}