package org.academy.internal.common.structure.sable.reflect.api.neoforge.event;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent")
public interface ForgeSablePrePhysicsTickEvent extends org.academy.internal.common.structure.sable.reflect.SableView {
    org.academy.internal.common.structure.sable.reflect.api.sublevel.system.SubLevelPhysicsSystem getPhysicsSystem();
    double getTimeStep();
}
