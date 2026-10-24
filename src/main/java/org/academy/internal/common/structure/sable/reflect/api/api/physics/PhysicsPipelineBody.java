package org.academy.internal.common.structure.sable.reflect.api.api.physics;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.api.physics.PhysicsPipelineBody")
public interface PhysicsPipelineBody extends org.academy.internal.common.structure.sable.reflect.SableView {
    int NULL_RUNTIME_ID = -1;
    int getRuntimeId();
    org.academy.internal.common.structure.sable.reflect.api.api.physics.mass.MassData getMassTracker();
    boolean isRemoved();
}
