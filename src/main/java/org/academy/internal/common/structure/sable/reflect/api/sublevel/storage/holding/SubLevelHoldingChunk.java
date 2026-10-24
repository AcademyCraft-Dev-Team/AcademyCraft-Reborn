package org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.holding;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk")
public interface SubLevelHoldingChunk extends org.academy.internal.common.structure.sable.reflect.SableView {
    static org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.holding.SubLevelHoldingChunk create(net.minecraft.world.level.ChunkPos a0) { return SableReflection.construct(SubLevelHoldingChunk.class, new Class<?>[]{net.minecraft.world.level.ChunkPos.class}, a0); }
    java.lang.Iterable<org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.HoldingSubLevel> getLoadedHoldingSubLevels();
    void removeHoldingSubLevel(java.util.UUID a0);
    static org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.holding.SubLevelHoldingChunk from(net.minecraft.world.level.ChunkPos a0, net.minecraft.nbt.CompoundTag a1) { return SableReflection.callStatic(SubLevelHoldingChunk.class, "from", new Class<?>[]{net.minecraft.world.level.ChunkPos.class, net.minecraft.nbt.CompoundTag.class}, a0, a1); }
    void writeTo(net.minecraft.nbt.CompoundTag a0);
    java.util.List<org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.holding.SavedSubLevelPointer> getSubLevelPointers();
    boolean isEmpty();
    java.lang.String toString();
}
