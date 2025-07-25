package org.academy.api.client.input;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public class MouseButtonEvent extends Event implements ICancellableEvent {
    public int button;
    public int action;
    public int modifiers;

    public MouseButtonEvent(int newButton, int newAction, int newModifiers) {
        button = newButton;
        action = newAction;
        modifiers = newModifiers;
    }
}