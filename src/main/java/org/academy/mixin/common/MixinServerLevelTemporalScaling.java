package org.academy.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.LevelTicks;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.server.time.TemporalRuntime;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.BiConsumer;

@Mixin(value = ServerLevel.class, priority = 1900)
public abstract class MixinServerLevelTemporalScaling {
    @Shadow
    protected abstract void tickTime();

    @Shadow
    @Final
    private List<BlockEventData> blockEventsToReschedule;

    @Invoker("advanceWeatherCycle")
    protected abstract void academy$invokeAdvanceWeatherCycle();

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
                    target = "Lnet/minecraft/world/level/border/WorldBorder;tick()V"
            )
    )
    private void academy$dispatchWorldBorderTicks(WorldBorder border) {
        var level = (ServerLevel) (Object) this;
        var context = (MinecraftServerContext) level.getServer();
        if (!context.hasAcademyCraftServer()) {
            border.tick();
            return;
        }
        ((TemporalRuntime) context.getAcademyCraftServer().getTemporalService())
                .dispatchWorldBorderTicks(level, border::tick);
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;advanceWeatherCycle()V"
            )
    )
    private void academy$dispatchWeatherTicks(ServerLevel level) {
        var context = (MinecraftServerContext) level.getServer();
        if (!context.hasAcademyCraftServer()) {
            academy$invokeAdvanceWeatherCycle();
            return;
        }
        ((TemporalRuntime) context.getAcademyCraftServer().getTemporalService())
                .dispatchWeatherTicks(
                        level,
                        this::academy$invokeAdvanceWeatherCycle
                );
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/raid/Raids;tick(Lnet/minecraft/server/level/ServerLevel;)V"
            )
    )
    private void academy$dispatchRaidTicks(Raids raids, ServerLevel level) {
        var context = (MinecraftServerContext) level.getServer();
        if (!context.hasAcademyCraftServer()) {
            raids.tick(level);
            return;
        }
        ((TemporalRuntime) context.getAcademyCraftServer().getTemporalService())
                .dispatchRaidTicks(level, () -> raids.tick(level));
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/dimension/end/EnderDragonFight;tick()V"
            )
    )
    private void academy$dispatchDragonFightTicks(EnderDragonFight fight) {
        var level = (ServerLevel) (Object) this;
        var context = (MinecraftServerContext) level.getServer();
        if (!context.hasAcademyCraftServer()) {
            fight.tick();
            return;
        }
        ((TemporalRuntime) context.getAcademyCraftServer().getTemporalService())
                .dispatchDragonFightTicks(level, fight::tick);
    }

    @Inject(
            method = "tickCustomSpawners",
            at = @At("HEAD"),
            cancellable = true
    )
    private void academy$dispatchCustomSpawnerTicks(
            boolean spawnEnemies,
            CallbackInfo ci
    ) {
        var level = (ServerLevel) (Object) this;
        var context = (MinecraftServerContext) level.getServer();
        if (!context.hasAcademyCraftServer()) return;
        var runtime = (TemporalRuntime) context.getAcademyCraftServer()
                .getTemporalService();
        if (runtime.dispatchCustomSpawnerTicks(
                level,
                () -> level.tickCustomSpawners(spawnEnemies)
        )) {
            ci.cancel();
        }
    }

    @Inject(method = "doBlockEvent", at = @At("HEAD"), cancellable = true)
    private void academy$deferScaledBlockEvent(
            BlockEventData eventData,
            CallbackInfoReturnable<Boolean> cir
    ) {
        var level = (ServerLevel) (Object) this;
        var context = (MinecraftServerContext) level.getServer();
        if (!context.hasAcademyCraftServer()) return;
        var runtime = (TemporalRuntime) context.getAcademyCraftServer()
                .getTemporalService();
        if (runtime.deferBlockEvent(level, eventData)) {
            blockEventsToReschedule.add(eventData);
            cir.setReturnValue(false);
        }
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

    @Redirect(
            method = "tickChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;tickPrecipitation(Lnet/minecraft/core/BlockPos;)V"
            )
    )
    private void academy$dispatchPrecipitationTicks(
            ServerLevel level,
            BlockPos position
    ) {
        var context = (MinecraftServerContext) level.getServer();
        if (!context.hasAcademyCraftServer()) {
            level.tickPrecipitation(position);
            return;
        }
        ((TemporalRuntime) context.getAcademyCraftServer().getTemporalService())
                .dispatchPrecipitationTicks(
                        level,
                        position,
                        () -> level.tickPrecipitation(position)
                );
    }
}
