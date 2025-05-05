package org.academy.api.client.input;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public class KeyEvent extends Event implements ICancellableEvent {
    public int key;
    public int action;
    public int modifiers;

    public KeyEvent(int key, int action, int modifiers) {
        this.key = key;
        this.action = action;
        this.modifiers = modifiers;
    }
}
