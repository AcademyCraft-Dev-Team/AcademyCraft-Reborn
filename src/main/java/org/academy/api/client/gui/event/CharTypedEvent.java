package org.academy.api.client.gui.event;

public final class CharTypedEvent extends InputEvent {
    private final int codePoint;
    private final int modifiers;

    public CharTypedEvent(int codePoint, int modifiers) {
        super(EventType.CHAR_TYPED);
        this.codePoint = codePoint;
        this.modifiers = modifiers;
    }

    public int getCodePoint() {
        return codePoint;
    }

    public int getModifiers() {
        return modifiers;
    }
}