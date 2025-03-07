package org.academy.mixin;

import net.minecraft.server.Main;
import org.academy.AcademyCraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
public class MainMixin {
    @Inject(method = "main", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;startTimerHackThread()V"))
    private static void onMain(CallbackInfo ci) {
        AcademyCraft.init();
    }
}
