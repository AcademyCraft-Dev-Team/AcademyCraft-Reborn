package org.academy.mixin.client;

import org.academy.api.client.input.InputSystem;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = InputSystem.class, priority = 1, remap = false)
public abstract class MixinTemporalInputBoundary {
}
