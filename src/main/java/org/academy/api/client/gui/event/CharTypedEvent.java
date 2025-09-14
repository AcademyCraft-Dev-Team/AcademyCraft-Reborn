package org.academy.api.client.gui.event;

public final class CharTypedEvent extends InputEvent {
    private final char codePoint;
    private final int modifiers;

    public CharTypedEvent(char codePoint, int modifiers) {
        super(EventType.CHAR_TYPED);
        this.codePoint = codePoint;
        this.modifiers = modifiers;
    }

    public char getCodePoint() {
        return codePoint;
    }

    public int getModifiers() {
        return modifiers;
    }
}