package org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.holding;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer")
public interface GlobalSavedSubLevelPointer extends org.academy.internal.common.structure.sable.reflect.SableView {
    static org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.holding.GlobalSavedSubLevelPointer create(net.minecraft.world.level.ChunkPos a0, short a1, short a2) { return SableReflection.construct(GlobalSavedSubLevelPointer.class, new Class<?>[]{net.minecraft.world.level.ChunkPos.class, short.class, short.class}, a0, a1, a2); }
    org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.holding.SavedSubLevelPointer local();
    java.lang.String toString();
    int hashCode();
    boolean equals(java.lang.Object a0);
    net.minecraft.world.level.ChunkPos chunkPos();
    short storageIndex();
    short subLevelIndex();
}
