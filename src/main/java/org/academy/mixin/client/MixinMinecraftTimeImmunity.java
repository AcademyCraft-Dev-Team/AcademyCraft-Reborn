package org.academy.mixin.client;

import net.minecraft.client.Minecraft;
import org.academy.internal.client.time.TemporalClientRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, priority = 2000)
public abstract class MixinMinecraftTimeImmunity {
    @Inject(method = "tick", at = @At("HEAD"))
    private void academy$beforeTemporalClientTick(CallbackInfo ci) {
        TemporalClientRuntime.beforeVanillaTick((Minecraft) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void academy$afterTemporalClientTick(CallbackInfo ci) {
        TemporalClientRuntime.afterVanillaTick((Minecraft) (Object) this);
    }

    @Inject(method = "runTick", at = @At("TAIL"))
    private void academy$afterTemporalFrame(CallbackInfo ci) {
        TemporalClientRuntime.afterFrame((Minecraft) (Object) this);
    }
}
