package org.academy.internal.common.structure.sable.reflect.api.companion;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.companion.ClientSubLevelAccess")
public interface ClientSubLevelAccess extends org.academy.internal.common.structure.sable.reflect.api.companion.SubLevelAccess, org.academy.internal.common.structure.sable.reflect.SableView {
    org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3dc renderPose();
    org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3dc renderPose(float a0);
}
