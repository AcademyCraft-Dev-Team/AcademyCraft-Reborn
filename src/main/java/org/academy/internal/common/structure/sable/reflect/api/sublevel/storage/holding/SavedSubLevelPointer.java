package org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.holding;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer")
public interface SavedSubLevelPointer extends org.academy.internal.common.structure.sable.reflect.SableView {
    static org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.holding.SavedSubLevelPointer create(short a0, short a1) { return SableReflection.construct(SavedSubLevelPointer.class, new Class<?>[]{short.class, short.class}, a0, a1); }
    int packed();
    static org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.holding.SavedSubLevelPointer unpack(int a0) { return SableReflection.callStatic(SavedSubLevelPointer.class, "unpack", new Class<?>[]{int.class}, a0); }
    java.lang.String toString();
    int hashCode();
    boolean equals(java.lang.Object a0);
    short storageIndex();
    short subLevelIndex();
}
