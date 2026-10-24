package org.academy.internal.common.structure.sable.reflect.api.physics.config.dimension_physics;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics")
public interface DimensionPhysics extends org.academy.internal.common.structure.sable.reflect.SableView {
    static org.academy.internal.common.structure.sable.reflect.api.physics.config.dimension_physics.DimensionPhysics createDefault(net.minecraft.world.level.Level a0) { return SableReflection.callStatic(DimensionPhysics.class, "createDefault", new Class<?>[]{net.minecraft.world.level.Level.class}, a0); }
    java.lang.String toString();
    int hashCode();
    boolean equals(java.lang.Object a0);
    net.minecraft.resources.Identifier dimension();
    int priority();
}
