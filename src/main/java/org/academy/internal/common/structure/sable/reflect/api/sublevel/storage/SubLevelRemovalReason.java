package org.academy.internal.common.structure.sable.reflect.api.sublevel.storage;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason")
public interface SubLevelRemovalReason extends org.academy.internal.common.structure.sable.reflect.SableView {
    org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.SubLevelRemovalReason UNLOADED = SableReflection.field(SubLevelRemovalReason.class, "UNLOADED");
    org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.SubLevelRemovalReason REMOVED = SableReflection.field(SubLevelRemovalReason.class, "REMOVED");
    static org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.SubLevelRemovalReason[] values() { return SableReflection.callStatic(SubLevelRemovalReason.class, "values", new Class<?>[]{}); }
    static org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.SubLevelRemovalReason valueOf(java.lang.String a0) { return SableReflection.callStatic(SubLevelRemovalReason.class, "valueOf", new Class<?>[]{java.lang.String.class}, a0); }
}
