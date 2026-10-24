package org.academy.internal.common.structure.sable.reflect.api.companion.math;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.companion.math.Pose3d")
public interface Pose3d extends org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3dc, org.academy.internal.common.structure.sable.reflect.SableView {
    static org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3d create(org.joml.Vector3d a0, org.joml.Quaterniond a1, org.joml.Vector3d a2, org.joml.Vector3d a3) { return SableReflection.construct(Pose3d.class, new Class<?>[]{org.joml.Vector3d.class, org.joml.Quaterniond.class, org.joml.Vector3d.class, org.joml.Vector3d.class}, a0, a1, a2, a3); }
    static org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3d create() { return SableReflection.construct(Pose3d.class, new Class<?>[]{}); }
    static org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3d create(org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3dc a0) { return SableReflection.construct(Pose3d.class, new Class<?>[]{org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3dc.class}, a0); }
    org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3d set(org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3dc a0);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3d lerp(org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3dc a0, double a1);
    org.joml.Vector3d position();
    org.joml.Quaterniond orientation();
    org.joml.Vector3d rotationPoint();
    org.joml.Vector3d scale();
    java.lang.String toString();
}
