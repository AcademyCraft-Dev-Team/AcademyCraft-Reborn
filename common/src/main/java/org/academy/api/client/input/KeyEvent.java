package org.academy.api.client.input;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public class KeyEvent extends Event implements ICancellableEvent {
    public int key;
    public int scanCode;
    public int action;
    public int modifiers;

    public KeyEvent(int newKey, int newScanCode, int newAction, int newModifiers) {
        key = newKey;
        scanCode = newScanCode;
        action = newAction;
        modifiers = newModifiers;
    }
}