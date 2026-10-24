package org.academy.internal.common.structure.sable.reflect.api.sublevel.storage;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel")
public interface HoldingSubLevel extends org.academy.internal.common.structure.sable.reflect.SableView {
    org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.serialization.SubLevelData data();
    org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.holding.GlobalSavedSubLevelPointer pointer();
    boolean equals(java.lang.Object a0);
    int hashCode();
    java.lang.String toString();
}
