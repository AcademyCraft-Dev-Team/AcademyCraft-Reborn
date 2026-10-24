package org.academy.internal.common.structure.sable.reflect.api.api.physics.object.box;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.api.physics.object.box.BoxHandle")
public interface BoxHandle extends org.academy.internal.common.structure.sable.reflect.SableView {
    void readPose(org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3d a0);
    void remove();
    int getRuntimeId();
}
