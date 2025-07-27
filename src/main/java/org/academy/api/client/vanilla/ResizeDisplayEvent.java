package org.academy.api.client.vanilla;

import net.neoforged.bus.api.Event;

public final class ResizeDisplayEvent extends Event {
    private final int width, height;

    public ResizeDisplayEvent(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}