package org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.region;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.storage.region.SubLevelRegionFile")
public interface SubLevelRegionFile extends org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.region.SubLevelStorageFile, org.academy.internal.common.structure.sable.reflect.SableView {
    org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.holding.SubLevelHoldingChunk read(net.minecraft.world.level.ChunkPos a0);
    int getIndex(int a0, int a1);
}
