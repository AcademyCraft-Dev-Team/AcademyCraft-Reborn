package org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.serialization;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData")
public interface SubLevelData extends org.academy.internal.common.structure.sable.reflect.SableView {
    static org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.serialization.SubLevelData fromTag(net.minecraft.nbt.CompoundTag a0) { return SableReflection.callStatic(SubLevelData.class, "fromTag", new Class<?>[]{net.minecraft.nbt.CompoundTag.class}, a0); }
    java.util.UUID uuid();
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d bounds();
    org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3d pose();
    java.util.List<java.util.UUID> dependencies();
    net.minecraft.nbt.CompoundTag fullTag();
    boolean equals(java.lang.Object a0);
    int hashCode();
    java.lang.String toString();
}
