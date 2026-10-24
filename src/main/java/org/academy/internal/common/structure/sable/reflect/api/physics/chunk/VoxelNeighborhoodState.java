package org.academy.internal.common.structure.sable.reflect.api.physics.chunk;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState")
public interface VoxelNeighborhoodState extends org.academy.internal.common.structure.sable.reflect.SableView {
    static org.academy.internal.common.structure.sable.reflect.api.physics.chunk.VoxelNeighborhoodState[] values() { return SableReflection.callStatic(VoxelNeighborhoodState.class, "values", new Class<?>[]{}); }
    static org.academy.internal.common.structure.sable.reflect.api.physics.chunk.VoxelNeighborhoodState valueOf(java.lang.String a0) { return SableReflection.callStatic(VoxelNeighborhoodState.class, "valueOf", new Class<?>[]{java.lang.String.class}, a0); }
    static boolean isLiquid(net.minecraft.world.level.block.state.BlockState a0) { return SableReflection.callStatic(VoxelNeighborhoodState.class, "isLiquid", new Class<?>[]{net.minecraft.world.level.block.state.BlockState.class}, a0); }
    static org.academy.internal.common.structure.sable.reflect.api.physics.chunk.VoxelNeighborhoodState getState(org.academy.internal.common.structure.sable.reflect.api.util.LevelAccelerator a0, net.minecraft.core.BlockPos a1, net.minecraft.world.level.chunk.LevelChunk a2) { return SableReflection.callStatic(VoxelNeighborhoodState.class, "getState", new Class<?>[]{org.academy.internal.common.structure.sable.reflect.api.util.LevelAccelerator.class, net.minecraft.core.BlockPos.class, net.minecraft.world.level.chunk.LevelChunk.class}, a0, a1, a2); }
}
