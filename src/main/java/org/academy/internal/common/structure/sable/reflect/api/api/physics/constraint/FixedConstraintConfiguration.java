package org.academy.internal.common.structure.sable.reflect.api.api.physics.constraint;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration")
public interface FixedConstraintConfiguration extends org.academy.internal.common.structure.sable.reflect.api.api.physics.constraint.PhysicsConstraintConfiguration<org.academy.internal.common.structure.sable.reflect.api.api.physics.constraint.FixedConstraintHandle>, org.academy.internal.common.structure.sable.reflect.SableView {
    static org.academy.internal.common.structure.sable.reflect.api.api.physics.constraint.FixedConstraintConfiguration create(org.joml.Vector3dc a0, org.joml.Vector3dc a1, org.joml.Quaterniondc a2) { return SableReflection.construct(FixedConstraintConfiguration.class, new Class<?>[]{org.joml.Vector3dc.class, org.joml.Vector3dc.class, org.joml.Quaterniondc.class}, a0, a1, a2); }
    void validate(org.academy.internal.common.structure.sable.reflect.api.api.sublevel.ServerSubLevelContainer a0, org.academy.internal.common.structure.sable.reflect.api.api.physics.PhysicsPipelineBody a1, org.academy.internal.common.structure.sable.reflect.api.api.physics.PhysicsPipelineBody a2);
    java.lang.String toString();
    int hashCode();
    boolean equals(java.lang.Object a0);
    org.joml.Quaterniondc orientation();
}
