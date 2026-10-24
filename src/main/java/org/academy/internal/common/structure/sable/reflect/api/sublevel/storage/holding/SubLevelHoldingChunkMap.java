package org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.holding;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap")
public interface SubLevelHoldingChunkMap extends org.academy.internal.common.structure.sable.reflect.SableView {
    @org.academy.internal.common.structure.sable.reflect.SableField
    java.util.Set<GlobalSavedSubLevelPointer> queuedDeletion();
    @org.academy.internal.common.structure.sable.reflect.SableField
    java.util.Map<java.util.UUID, org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.HoldingSubLevel> allHoldingSubLevels();
    @org.academy.internal.common.structure.sable.reflect.SableField
    java.util.Map<Long, SubLevelHoldingChunk> loadedHoldingChunks();
    void updateChunkStatus(net.minecraft.world.level.ChunkPos a0, boolean a1);
    void saveAll();
    org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.serialization.SubLevelStorage getStorage();
    void close() throws java.lang.Exception;
}
