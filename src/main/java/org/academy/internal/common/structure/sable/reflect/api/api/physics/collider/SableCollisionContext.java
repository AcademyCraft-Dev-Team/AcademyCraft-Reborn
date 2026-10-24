package org.academy.internal.common.structure.sable.reflect.api.api.physics.collider;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.api.physics.collider.SableCollisionContext")
public interface SableCollisionContext extends org.academy.internal.common.structure.sable.reflect.SableView {
    static net.minecraft.world.phys.shapes.CollisionContext get() { return SableReflection.callStatic(SableCollisionContext.class, "get", new Class<?>[]{}); }
}
