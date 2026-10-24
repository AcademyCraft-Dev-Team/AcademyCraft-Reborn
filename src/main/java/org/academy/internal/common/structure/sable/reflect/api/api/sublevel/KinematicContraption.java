package org.academy.internal.common.structure.sable.reflect.api.api.sublevel;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.api.sublevel.KinematicContraption")
public interface KinematicContraption extends org.academy.internal.common.structure.sable.reflect.SableView {
    void sable$getLocalBounds(org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3i a0);
    boolean sable$shouldCollide();
    boolean sable$isValid();
    org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3d sable$getLocalPose(org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3d a0, double a1);
}
