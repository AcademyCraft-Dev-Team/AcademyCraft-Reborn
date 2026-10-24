package org.academy.internal.common.structure.sable.reflect.api.api.physics.constraint;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration")
public interface PhysicsConstraintConfiguration<T extends org.academy.internal.common.structure.sable.reflect.api.api.physics.constraint.PhysicsConstraintHandle> extends org.academy.internal.common.structure.sable.reflect.SableView {
    void validate(org.academy.internal.common.structure.sable.reflect.api.api.sublevel.ServerSubLevelContainer a0, org.academy.internal.common.structure.sable.reflect.api.api.physics.PhysicsPipelineBody a1, org.academy.internal.common.structure.sable.reflect.api.api.physics.PhysicsPipelineBody a2);
}
