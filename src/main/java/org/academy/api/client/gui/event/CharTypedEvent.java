package org.academy.api.client.gui.event;

public final class CharTypedEvent extends InputEvent {
    private final int codePoint;

    public CharTypedEvent(int codePoint) {
        super(EventType.CHAR_TYPED);
        this.codePoint = codePoint;
    }

    public int getCodePoint() {
        return codePoint;
    }
}