package org.academy.api.client.vanilla;

import net.neoforged.bus.api.Event;

public class ResizeDisplayEvent extends Event {
    public int width;
    public int height;

    public ResizeDisplayEvent(int width, int height) {
        this.width = width;
        this.height = height;
    }
}