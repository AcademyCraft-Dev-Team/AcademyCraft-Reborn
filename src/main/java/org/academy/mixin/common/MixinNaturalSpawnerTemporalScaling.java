package org.academy.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.server.time.TemporalRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Applies spatial temporal fields to natural-spawn category attempts. */
@Mixin(value = NaturalSpawner.class, priority = 1900)
public abstract class MixinNaturalSpawnerTemporalScaling {
    @Redirect(
            method = "spawnCategoryForChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/NaturalSpawner;spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V"
            )
    )
    private static void academy$dispatchNaturalSpawningTicks(
            MobCategory category,
            ServerLevel level,
            ChunkAccess chunk,
            BlockPos position,
            NaturalSpawner.SpawnPredicate predicate,
            NaturalSpawner.AfterSpawnCallback callback
    ) {
        var context = (MinecraftServerContext) level.getServer();
        if (!context.hasAcademyCraftServer()) {
            NaturalSpawner.spawnCategoryForPosition(
                    category,
                    level,
                    chunk,
                    position,
                    predicate,
                    callback
            );
            return;
        }
        var runtime = (TemporalRuntime) context.getAcademyCraftServer()
                .getTemporalService();
        runtime.dispatchNaturalSpawningTicks(
                level,
                position,
                () -> NaturalSpawner.spawnCategoryForPosition(
                        category,
                        level,
                        chunk,
                        position,
                        predicate,
                        callback
                )
        );
    }
}
