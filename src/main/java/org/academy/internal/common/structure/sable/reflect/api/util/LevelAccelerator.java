package org.academy.internal.common.structure.sable.reflect.api.util;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.util.LevelAccelerator")
public interface LevelAccelerator extends org.academy.internal.common.structure.sable.reflect.SableView {
    net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(net.minecraft.core.BlockPos a0);
    net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.core.BlockPos a0);
    net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.world.level.chunk.LevelChunk a0, net.minecraft.core.BlockPos a1);
    net.minecraft.world.level.material.FluidState getFluidState(net.minecraft.core.BlockPos a0);
    net.minecraft.world.level.chunk.LevelChunk getChunk(net.minecraft.core.BlockPos a0);
    net.minecraft.world.level.chunk.LevelChunk getChunk(int a0, int a1);
    boolean isOutsideBuildHeight(net.minecraft.core.Vec3i a0);
    int getHeight();
    int getMinY();
}
