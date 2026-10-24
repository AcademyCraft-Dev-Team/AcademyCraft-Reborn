package org.academy.internal.common.structure.sable.reflect.api.sublevel.system;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem")
public interface SubLevelTrackingSystem extends org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelObserver, org.academy.internal.common.structure.sable.reflect.SableView {
    void onSubLevelAdded(org.academy.internal.common.structure.sable.reflect.api.sublevel.SubLevel a0);
    void onSubLevelRemoved(org.academy.internal.common.structure.sable.reflect.api.sublevel.SubLevel a0, org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.SubLevelRemovalReason a1);
    void tick(org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer a0);
}
