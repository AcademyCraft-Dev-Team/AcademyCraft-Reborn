package org.academy.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.server.time.TemporalRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LevelAccessor.class, priority = 1900)
public interface MixinLevelAccessorTemporalScaling {
    @Inject(
            method = "createTick(Lnet/minecraft/core/BlockPos;Ljava/lang/Object;ILnet/minecraft/world/ticks/TickPriority;)Lnet/minecraft/world/ticks/ScheduledTick;",
            at = @At("HEAD"),
            cancellable = true
    )
    private <T> void academy$scalePrioritizedScheduledDelay(
            BlockPos position,
            T type,
            int delay,
            TickPriority priority,
            CallbackInfoReturnable<ScheduledTick<T>> cir
    ) {
        var accessor = (LevelAccessor) (Object) this;
        if (!(accessor instanceof ServerLevel level)) return;
        var context = (MinecraftServerContext) level.getServer();
        if (!context.hasAcademyCraftServer()) return;
        var runtime = (TemporalRuntime) context.getAcademyCraftServer()
                .getTemporalService();
        var scaledDelay = runtime.scaleScheduledDelay(
                level,
                position,
                type,
                delay
        );
        if (scaledDelay == delay) return;
        cir.setReturnValue(new ScheduledTick<>(
                type,
                position,
                level.getGameTime() + scaledDelay,
                priority,
                level.nextSubTickCount()
        ));
    }

    @Inject(
            method = "createTick(Lnet/minecraft/core/BlockPos;Ljava/lang/Object;I)Lnet/minecraft/world/ticks/ScheduledTick;",
            at = @At("HEAD"),
            cancellable = true
    )
    private <T> void academy$scaleScheduledDelay(
            BlockPos position,
            T type,
            int delay,
            CallbackInfoReturnable<ScheduledTick<T>> cir
    ) {
        var accessor = (LevelAccessor) (Object) this;
        if (!(accessor instanceof ServerLevel level)) return;
        var context = (MinecraftServerContext) level.getServer();
        if (!context.hasAcademyCraftServer()) return;
        var runtime = (TemporalRuntime) context.getAcademyCraftServer()
                .getTemporalService();
        var scaledDelay = runtime.scaleScheduledDelay(
                level,
                position,
                type,
                delay
        );
        if (scaledDelay == delay) return;
        cir.setReturnValue(new ScheduledTick<>(
                type,
                position,
                level.getGameTime() + scaledDelay,
                level.nextSubTickCount()
        ));
    }
}
