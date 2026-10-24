package org.academy.internal.common.structure.sable.reflect.api.api.physics.constraint;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle")
public interface PhysicsConstraintHandle extends org.academy.internal.common.structure.sable.reflect.SableView {
    void remove();
    boolean isValid();
}
