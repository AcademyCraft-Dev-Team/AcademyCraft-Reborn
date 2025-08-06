package org.academy.api.client.gui.event;

import org.academy.api.client.gui.framework.Widget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class InputEvent {
    private final EventType type;
    private boolean isConsumed = false;
    private Widget target;

    protected InputEvent(@NotNull EventType type) {
        this.type = type;
    }

    @NotNull
    public EventType getType() {
        return type;
    }

    public boolean isConsumed() {
        return isConsumed;
    }

    public void consume() {
        this.isConsumed = true;
    }

    @Nullable
    public Widget getTarget() {
        return target;
    }

    public void setTarget(@NotNull Widget target) {
        this.target = target;
    }
}