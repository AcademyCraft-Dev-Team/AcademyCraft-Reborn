package org.academy.internal.common.structure.sable.reflect.api.api.physics.object;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.api.physics.object.ArbitraryPhysicsObject")
public interface ArbitraryPhysicsObject extends org.academy.internal.common.structure.sable.reflect.SableView {
    void getBoundingBox(org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d a0);
}
