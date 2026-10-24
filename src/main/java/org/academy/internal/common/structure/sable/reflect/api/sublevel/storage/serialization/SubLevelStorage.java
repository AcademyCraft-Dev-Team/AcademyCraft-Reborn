package org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.serialization;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage")
public interface SubLevelStorage extends org.academy.internal.common.structure.sable.reflect.SableView {
    org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.region.SubLevelStorageFile getRegionStorageFile(net.minecraft.world.level.ChunkPos chunk, int index) throws java.io.IOException;
    org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.region.SubLevelRegionFile getRegionFile(net.minecraft.world.level.ChunkPos chunk) throws java.io.IOException;
    void close() throws java.io.IOException;
    void flush() throws java.io.IOException;
}
