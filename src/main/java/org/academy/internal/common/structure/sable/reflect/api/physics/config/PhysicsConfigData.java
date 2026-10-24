package org.academy.internal.common.structure.sable.reflect.api.physics.config;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.physics.config.PhysicsConfigData")
public interface PhysicsConfigData extends org.academy.internal.common.structure.sable.reflect.SableView {
    @org.academy.internal.common.structure.sable.reflect.SableField
    int solverIterations();
}
