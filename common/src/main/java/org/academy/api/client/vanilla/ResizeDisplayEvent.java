package org.academy.api.client.vanilla;

import net.neoforged.bus.api.Event;

public class ResizeDisplayEvent extends Event {
    public int width;
    public int height;

    public ResizeDisplayEvent(int newWidth, int newHeight) {
        width = newWidth;
        height = newHeight;
    }
}