package org.academy.internal.common.structure.sable.reflect.api.api.block;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape")
public interface BlockSubLevelCollisionShape extends org.academy.internal.common.structure.sable.reflect.SableView {
    net.minecraft.world.phys.shapes.VoxelShape getSubLevelCollisionShape(net.minecraft.world.level.BlockGetter a0, net.minecraft.world.level.block.state.BlockState a1);
}
