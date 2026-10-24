package org.academy.mixin.common.compat;

import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(value = SubLevelHoldingChunkMap.class, remap = false)
public interface SableHoldingDeletionAccessor {
    @Accessor("queuedDeletion")
    ObjectSet<GlobalSavedSubLevelPointer> academy$queuedDeletion();

    @Accessor("allHoldingSubLevels")
    Object2ObjectMap<UUID, HoldingSubLevel> academy$holdingSources();

    @Accessor("loadedHoldingChunks")
    Long2ObjectMap<SubLevelHoldingChunk> academy$holdingChunks();
}
