package org.academy.internal.common.structure.sable.reflect.api.sublevel.storage;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.storage.SubLevelOccupancySavedData")
public interface SubLevelOccupancySavedData extends org.academy.internal.common.structure.sable.reflect.SableView {
    void setDirty();
    java.lang.String FILE_ID = "sable_sub_level_occupancy";
    static org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.SubLevelOccupancySavedData getOrLoad(net.minecraft.server.level.ServerLevel a0) { return SableReflection.callStatic(SubLevelOccupancySavedData.class, "getOrLoad", new Class<?>[]{net.minecraft.server.level.ServerLevel.class}, a0); }
    net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag a0, net.minecraft.core.HolderLookup.Provider a1);
}
