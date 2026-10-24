package org.academy.internal.common.structure.sable.reflect.api.api;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.api.SubLevelAssemblyHelper$AssemblyTransform")
public interface SubLevelAssemblyHelper$AssemblyTransform extends org.academy.internal.common.structure.sable.reflect.SableView {
    static SubLevelAssemblyHelper$AssemblyTransform create(net.minecraft.core.BlockPos target, net.minecraft.core.BlockPos source,
            int rotationY, net.minecraft.world.level.block.Rotation rotation, net.minecraft.server.level.ServerLevel host) {
        return SableReflection.construct(SubLevelAssemblyHelper$AssemblyTransform.class,
                new Class<?>[]{net.minecraft.core.BlockPos.class, net.minecraft.core.BlockPos.class, int.class,
                        net.minecraft.world.level.block.Rotation.class, net.minecraft.server.level.ServerLevel.class},
                target, source, rotationY, rotation, host);
    }
    net.minecraft.world.phys.Vec3 apply(net.minecraft.world.phys.Vec3 a0);
    net.minecraft.core.BlockPos apply(net.minecraft.core.BlockPos a0);
    net.minecraft.world.level.block.state.BlockState apply(net.minecraft.world.level.block.state.BlockState a0);
    net.minecraft.server.level.ServerLevel getLevel();
    net.minecraft.world.level.block.Rotation getRotation();
}
