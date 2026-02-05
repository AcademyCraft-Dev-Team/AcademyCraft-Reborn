package org.academy.api.client.gui.event;

import net.minecraft.client.input.InputQuirks;

public final class KeyEvent extends InputEvent {
    private final int keyCode;
    private final int scanCode;
    private final int modifiers;

    public KeyEvent(EventType type, int keyCode, int scanCode, int modifiers) {
        super(type);
        if (type != EventType.KEY_PRESSED && type != EventType.KEY_RELEASED) {
            throw new IllegalArgumentException("This constructor is for KEY_PRESSED or KEY_RELEASED events.");
        }
        this.keyCode = keyCode;
        this.scanCode = scanCode;
        this.modifiers = modifiers;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public int getScanCode() {
        return scanCode;
    }

    public int getModifiers() {
        return modifiers;
    }

    public boolean hasAltDown() {
        return (getModifiers() & 4) != 0;
    }

    public boolean hasShiftDown() {
        return (getModifiers() & 1) != 0;
    }

    public boolean hasControlDown() {
        return (getModifiers() & 2) != 0;
    }

    public boolean hasControlDownWithQuirk() {
        return (getModifiers() & InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER) != 0;
    }
}