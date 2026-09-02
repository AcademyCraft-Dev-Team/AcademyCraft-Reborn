package org.academy.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.LevelTicks;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.server.time.TemporalRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;

@Mixin(value = ServerLevel.class, priority = 1900)
public abstract class MixinServerLevelTemporalScaling {
    @Shadow
    protected abstract void tickTime();

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void academy$dispatchScaledEntityTicks(
            Entity entity,
            CallbackInfo ci
    ) {
        var level = (ServerLevel) (Object) this;
        var context = (MinecraftServerContext) level.getServer();
        if (!context.hasAcademyCraftServer()) return;
        var runtime = (TemporalRuntime) context.getAcademyCraftServer()
                .getTemporalService();
        if (runtime.dispatchEntityTicks(level, entity)) ci.cancel();
    }

    @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
    private void academy$dispatchScaledLevelClock(CallbackInfo ci) {
        var level = (ServerLevel) (Object) this;
        var context = (MinecraftServerContext) level.getServer();
        if (!context.hasAcademyCraftServer()) return;
        var runtime = (TemporalRuntime) context.getAcademyCraftServer()
                .getTemporalService();
        if (runtime.dispatchLevelClockTicks(level, this::tickTime)) ci.cancel();
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/ticks/LevelTicks;tick(JILjava/util/function/BiConsumer;)V"
            )
    )
    private <T> void academy$dispatchScheduledTickQueue(
            LevelTicks<T> queue,
            long gameTime,
            int maxTicks,
            BiConsumer<BlockPos, T> callback
    ) {
        var level = (ServerLevel) (Object) this;
        var context = (MinecraftServerContext) level.getServer();
        if (!context.hasAcademyCraftServer()) {
            queue.tick(gameTime, maxTicks, callback);
            return;
        }
        ((TemporalRuntime) context.getAcademyCraftServer().getTemporalService())
                .dispatchScheduledQueue(
                        level,
                        queue,
                        gameTime,
                        maxTicks,
                        callback
                );
    }

    @Redirect(
            method = "tickChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"
            )
    )
    private void academy$dispatchRandomBlockTick(
            BlockState state,
            ServerLevel level,
            BlockPos position,
            RandomSource random
    ) {
        var context = (MinecraftServerContext) level.getServer();
        if (!context.hasAcademyCraftServer()) {
            state.randomTick(level, position, random);
            return;
        }
        ((TemporalRuntime) context.getAcademyCraftServer().getTemporalService())
                .dispatchRandomBlockTick(state, level, position, random);
    }

    @Redirect(
            method = "tickChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/material/FluidState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"
            )
    )
    private void academy$dispatchRandomFluidTick(
            FluidState state,
            ServerLevel level,
            BlockPos position,
            RandomSource random
    ) {
        var context = (MinecraftServerContext) level.getServer();
        if (!context.hasAcademyCraftServer()) {
            state.randomTick(level, position, random);
            return;
        }
        ((TemporalRuntime) context.getAcademyCraftServer().getTemporalService())
                .dispatchRandomFluidTick(state, level, position, random);
    }
}
