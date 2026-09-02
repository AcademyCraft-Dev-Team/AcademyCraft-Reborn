package org.academy.mixin.common;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.server.time.TemporalRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps chunk infrastructure live while scaling world-simulation callbacks. */
@Mixin(value = ServerChunkCache.class, priority = 1900)
public abstract class MixinServerChunkCacheTemporalScaling {
    @Redirect(
            method = "tickSpawningChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;tickThunder(Lnet/minecraft/world/level/chunk/LevelChunk;)V"
            )
    )
    private void academy$dispatchThunderTicks(
            ServerLevel level,
            LevelChunk chunk
    ) {
        var runtime = academy$runtime(level);
        if (runtime == null) {
            level.tickThunder(chunk);
            return;
        }
        runtime.dispatchThunderTicks(
                level,
                chunk.getPos().getMiddleBlockPosition(level.getSeaLevel()),
                () -> level.tickThunder(chunk)
        );
    }

    private static TemporalRuntime academy$runtime(ServerLevel level) {
        var context = (MinecraftServerContext) level.getServer();
        if (!context.hasAcademyCraftServer()) return null;
        return (TemporalRuntime) context.getAcademyCraftServer()
                .getTemporalService();
    }
}
