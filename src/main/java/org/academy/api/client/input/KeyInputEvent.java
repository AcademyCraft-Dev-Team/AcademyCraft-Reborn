package org.academy.api.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public class KeyInputEvent extends Event implements ICancellableEvent {
    public @InputConstants.Value int key;
    public int scanCode;
    public @KeyEvent.Action int action;
    public @InputWithModifiers.Modifiers int modifiers;

    public KeyInputEvent(
            @InputConstants.Value int key,
            int scanCode,
            @KeyEvent.Action int action,
            @InputWithModifiers.Modifiers int modifiers
    ) {
        this.key = key;
        this.scanCode = scanCode;
        this.action = action;
        this.modifiers = modifiers;
    }
}