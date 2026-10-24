package org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.serialization;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer")
public interface SubLevelSerializer extends org.academy.internal.common.structure.sable.reflect.SableView {
    static org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.serialization.SubLevelData toData(org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel a0, java.util.List<java.util.UUID> a1) { return SableReflection.callStatic(SubLevelSerializer.class, "toData", new Class<?>[]{org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel.class, java.util.List.class}, a0, a1); }
}
