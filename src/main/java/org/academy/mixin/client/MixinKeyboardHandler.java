package org.academy.mixin.client;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;
import org.academy.api.client.hud.terminal.TerminalHud;
import org.academy.api.client.input.InputSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * For InputSystem
 */
@Mixin(KeyboardHandler.class)
public abstract class MixinKeyboardHandler {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void keyPress(long handle, @KeyEvent.Action int action, KeyEvent event, CallbackInfo ci) {
        InputSystem.handleKey(action, event, ci);
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void charTyped(long handle, CharacterEvent event, CallbackInfo ci) {
        if (TerminalHud.Companion.handleCharacterInput(event)) {
            ci.cancel();
        }
    }

    @Inject(method = "preeditCallback", at = @At("HEAD"), cancellable = true)
    private void preeditCallback(long handle, PreeditEvent event, CallbackInfo ci) {
        if (TerminalHud.Companion.handlePreeditInput(event)) {
            ci.cancel();
        }
    }
}
