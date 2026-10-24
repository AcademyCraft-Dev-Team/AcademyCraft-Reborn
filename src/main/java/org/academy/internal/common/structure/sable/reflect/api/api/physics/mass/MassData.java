package org.academy.internal.common.structure.sable.reflect.api.api.physics.mass;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.api.physics.mass.MassData")
public interface MassData extends org.academy.internal.common.structure.sable.reflect.SableView {
    double getMass();
    org.joml.Matrix3dc getInertiaTensor();
    org.joml.Vector3dc getCenterOfMass();
    boolean isInvalid();
}
