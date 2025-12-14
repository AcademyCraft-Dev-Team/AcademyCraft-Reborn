package org.academy.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.vanilla.MainLoopEvent;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Shadow
    @Nullable
    public Screen screen;

    @Inject(method = "runTick", at = @At("HEAD"))
    private void runTick(CallbackInfo info) {
        NeoForge.EVENT_BUS.post(new MainLoopEvent());
    }
}