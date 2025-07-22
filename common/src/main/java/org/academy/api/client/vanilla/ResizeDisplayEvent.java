package org.academy.api.client.vanilla;

import com.mojang.blaze3d.platform.Window;
import net.neoforged.bus.api.Event;

public class ResizeDisplayEvent extends Event {
    private final Window window;

    public ResizeDisplayEvent(Window window) {
        this.window = window;
    }

    public Window getWindow() {
        return window;
    }
}