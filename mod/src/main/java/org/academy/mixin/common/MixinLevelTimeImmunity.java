package org.academy.mixin.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.server.time.TemporalRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(value = Level.class, priority = 2000)
public abstract class MixinLevelTimeImmunity {
    @Inject(method = "guardEntityTick", at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void academy$bypassTemporalGuardCancellation(
            Consumer<T> tick,
            T entity,
            CallbackInfo ci
    ) {
        var level = (Level) (Object) this;
        var server = level.getServer();
        if (server == null) return;
        var context = (MinecraftServerContext) server;
        if (!context.hasAcademyCraftServer()) return;
        var runtime = (TemporalRuntime) context.getAcademyCraftServer().getTemporalService();
        if (!runtime.tryEnterGuardBypass(level, entity)) return;

        try {
            tick.accept(entity);
            ci.cancel();
        } finally {
            runtime.exitGuardBypass(entity);
        }
    }
}
