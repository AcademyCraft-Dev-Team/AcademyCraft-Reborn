package org.academy.mixin.client;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = {KeyboardHandler.class, MouseHandler.class, ClientLevel.class}, priority = 1)
public abstract class MixinTemporalClientBoundary {
}
