package org.academy.internal.common.structure.sable.reflect.api.physics.config.dimension_physics;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData")
public interface DimensionPhysicsData extends org.academy.internal.common.structure.sable.reflect.SableView {
    static org.academy.internal.common.structure.sable.reflect.api.physics.config.dimension_physics.DimensionPhysics of(net.minecraft.world.level.Level a0) { return SableReflection.callStatic(DimensionPhysicsData.class, "of", new Class<?>[]{net.minecraft.world.level.Level.class}, a0); }
    static org.academy.internal.common.structure.sable.reflect.api.physics.config.dimension_physics.DimensionPhysics getDefault(net.minecraft.world.level.Level a0) { return SableReflection.callStatic(DimensionPhysicsData.class, "getDefault", new Class<?>[]{net.minecraft.world.level.Level.class}, a0); }
    static org.joml.Vector3d getGravity(net.minecraft.world.level.Level a0) { return SableReflection.callStatic(DimensionPhysicsData.class, "getGravity", new Class<?>[]{net.minecraft.world.level.Level.class}, a0); }
    static org.joml.Vector3d getGravity(net.minecraft.world.level.Level a0, org.joml.Vector3dc a1) { return SableReflection.callStatic(DimensionPhysicsData.class, "getGravity", new Class<?>[]{net.minecraft.world.level.Level.class, org.joml.Vector3dc.class}, a0, a1); }
    static org.joml.Vector3d getGravity(net.minecraft.world.level.Level a0, org.joml.Vector3dc a1, org.joml.Vector3d a2) { return SableReflection.callStatic(DimensionPhysicsData.class, "getGravity", new Class<?>[]{net.minecraft.world.level.Level.class, org.joml.Vector3dc.class, org.joml.Vector3d.class}, a0, a1, a2); }
}
