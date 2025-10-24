package org.academy.api.client.gui.layout;

public final class MeasureSpec {
    private final Mode mode;
    private final float size;

    public enum Mode {
        EXACTLY,
        AT_MOST,
        UNSPECIFIED
    }

    public MeasureSpec(Mode mode, float size) {
        this.mode = mode;
        this.size = size;
    }

    public Mode getMode() {
        return mode;
    }

    public float getSize() {
        return size;
    }
}