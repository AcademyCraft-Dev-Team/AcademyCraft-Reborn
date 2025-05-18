package org.academy.api.client.vanilla;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class ChangeScreenEvent extends Event {
    public final Screen currentScreen;
    public Screen newScreen;

    public ChangeScreenEvent(Screen currentScreen, Screen newScreen) {
        this.currentScreen = currentScreen;
        this.newScreen = newScreen;
    }

    public static final class Pre extends ChangeScreenEvent implements ICancellableEvent {
        public Pre(Screen currentScreen, Screen newScreen) {
            super(currentScreen, newScreen);
        }
    }

    public static final class Post extends ChangeScreenEvent {
        public Post(Screen currentScreen, Screen newScreen) {
            super(currentScreen, newScreen);
        }
    }
}