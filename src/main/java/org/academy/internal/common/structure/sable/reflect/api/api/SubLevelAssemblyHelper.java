package org.academy.internal.common.structure.sable.reflect.api.api;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.api.SubLevelAssemblyHelper")
public interface SubLevelAssemblyHelper extends org.academy.internal.common.structure.sable.reflect.SableView {
    static org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel assembleBlocks(net.minecraft.server.level.ServerLevel a0, net.minecraft.core.BlockPos a1, java.lang.Iterable<net.minecraft.core.BlockPos> a2, org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3ic a3) { return SableReflection.callStatic(SubLevelAssemblyHelper.class, "assembleBlocks", new Class<?>[]{net.minecraft.server.level.ServerLevel.class, net.minecraft.core.BlockPos.class, java.lang.Iterable.class, org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3ic.class}, a0, a1, a2, a3); }
    static void moveBlocks(net.minecraft.server.level.ServerLevel a0, org.academy.internal.common.structure.sable.reflect.api.api.SubLevelAssemblyHelper$AssemblyTransform a1, java.lang.Iterable<net.minecraft.core.BlockPos> a2) { SableReflection.callStatic(SubLevelAssemblyHelper.class, "moveBlocks", new Class<?>[]{net.minecraft.server.level.ServerLevel.class, org.academy.internal.common.structure.sable.reflect.api.api.SubLevelAssemblyHelper$AssemblyTransform.class, java.lang.Iterable.class}, a0, a1, a2); }
}
