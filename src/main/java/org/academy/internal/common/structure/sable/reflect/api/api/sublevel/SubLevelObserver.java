package org.academy.internal.common.structure.sable.reflect.api.api.sublevel;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.api.sublevel.SubLevelObserver")
public interface SubLevelObserver extends org.academy.internal.common.structure.sable.reflect.SableView {
    default void onSubLevelAdded(org.academy.internal.common.structure.sable.reflect.api.sublevel.SubLevel a0) { }
    default void onSubLevelRemoved(org.academy.internal.common.structure.sable.reflect.api.sublevel.SubLevel a0, org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.SubLevelRemovalReason a1) { }
    default void tick(org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer a0) { }
}
