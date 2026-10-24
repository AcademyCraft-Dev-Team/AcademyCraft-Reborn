package org.academy.mixin.common.compat;

import dev.ryanhcode.sable.sublevel.storage.region.SubLevelRegionFile;
import dev.ryanhcode.sable.sublevel.storage.region.SubLevelStorageFile;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.IOException;

/** Uses the pinned backend's throwing I/O operations instead of its log-and-swallow wrappers. */
@Mixin(value = SubLevelStorage.class, remap = false)
public interface SableStorageInvoker {
    @Invoker("getRegionStorageFile")
    SubLevelStorageFile academy$storageFile(ChunkPos chunk, int index) throws IOException;

    @Invoker("getRegionFile")
    SubLevelRegionFile academy$regionFile(ChunkPos chunk) throws IOException;
}
