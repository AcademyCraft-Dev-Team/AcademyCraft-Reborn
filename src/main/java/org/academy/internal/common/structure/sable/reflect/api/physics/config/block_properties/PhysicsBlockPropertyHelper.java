package org.academy.internal.common.structure.sable.reflect.api.physics.config.block_properties;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper")
public interface PhysicsBlockPropertyHelper extends org.academy.internal.common.structure.sable.reflect.SableView {
    static double getMass(net.minecraft.world.level.BlockGetter a0, net.minecraft.core.BlockPos a1, net.minecraft.world.level.block.state.BlockState a2) { return SableReflection.callStatic(PhysicsBlockPropertyHelper.class, "getMass", new Class<?>[]{net.minecraft.world.level.BlockGetter.class, net.minecraft.core.BlockPos.class, net.minecraft.world.level.block.state.BlockState.class}, a0, a1, a2); }
    static double getFriction(net.minecraft.world.level.block.state.BlockState a0) { return SableReflection.callStatic(PhysicsBlockPropertyHelper.class, "getFriction", new Class<?>[]{net.minecraft.world.level.block.state.BlockState.class}, a0); }
    static double getVolume(net.minecraft.world.level.block.state.BlockState a0) { return SableReflection.callStatic(PhysicsBlockPropertyHelper.class, "getVolume", new Class<?>[]{net.minecraft.world.level.block.state.BlockState.class}, a0); }
}
