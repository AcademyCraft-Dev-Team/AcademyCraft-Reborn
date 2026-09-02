package org.academy.mixin.common;

import net.minecraft.server.level.ServerLevel;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.server.time.TemporalRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(value = ServerLevel.class, priority = 2000)
public abstract class MixinServerLevelTimeImmunity {
    @Inject(method = "tick", at = @At("HEAD"))
    private void academy$beginTemporalLevelTick(
            BooleanSupplier haveTime,
            CallbackInfo ci
    ) {
        var level = (ServerLevel) (Object) this;
        var context = (MinecraftServerContext) level.getServer();
        if (!context.hasAcademyCraftServer()) return;
        ((TemporalRuntime) context.getAcademyCraftServer().getTemporalService())
                .beginLevelTick(level);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void academy$finishTemporalLevelTick(
            BooleanSupplier haveTime,
            CallbackInfo ci
    ) {
        var level = (ServerLevel) (Object) this;
        var context = (MinecraftServerContext) level.getServer();
        if (!context.hasAcademyCraftServer()) return;
        ((TemporalRuntime) context.getAcademyCraftServer().getTemporalService())
                .finishLevelTick(level);
    }
}
