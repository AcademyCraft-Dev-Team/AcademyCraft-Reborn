package org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.region;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.storage.region.SubLevelStorageFile")
public interface SubLevelStorageFile extends org.academy.internal.common.structure.sable.reflect.SableView {
    boolean isEmpty();
    void write(int a0, net.minecraft.nbt.CompoundTag a1) throws java.io.IOException;
    net.minecraft.nbt.CompoundTag read(int a0) throws java.io.IOException;
    java.nio.file.Path getPath();
    void close() throws java.io.IOException;
    void delete();
    void flush() throws java.io.IOException;
}
