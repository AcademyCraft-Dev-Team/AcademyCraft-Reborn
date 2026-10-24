package org.academy.internal.common.structure.sable.reflect.api.companion;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.companion.SubLevelAccess")
public interface SubLevelAccess extends org.academy.internal.common.structure.sable.reflect.SableView {
    org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3dc logicalPose();
    org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3dc lastPose();
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3dc boundingBox();
    java.util.UUID getUniqueId();
    java.lang.String getName();
}
