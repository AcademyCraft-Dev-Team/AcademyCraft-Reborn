package org.academy.mixin.common;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the per-chunk save used by ChunkMap.saveAllChunks for scoped durability. */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
    @Invoker("save")
    boolean academy$save(ChunkAccess chunk);
}
