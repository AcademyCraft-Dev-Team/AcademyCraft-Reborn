package org.academy.api.client.gui.event;

import org.academy.api.client.gui.widget.Widget;
import org.jetbrains.annotations.Nullable;

public abstract class InputEvent {
    private final EventType type;
    private boolean isConsumed = false;
    @Nullable
    private Widget target;

    protected InputEvent(EventType type) {
        this.type = type;
    }

    public EventType getType() {
        return type;
    }

    public boolean isConsumed() {
        return isConsumed;
    }

    public void consume() {
        isConsumed = true;
    }

    @Nullable
    public Widget getTarget() {
        return target;
    }

    public void setTarget(Widget target) {
        this.target = target;
    }
}