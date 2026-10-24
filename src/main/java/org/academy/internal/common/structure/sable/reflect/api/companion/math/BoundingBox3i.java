package org.academy.internal.common.structure.sable.reflect.api.companion.math;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.companion.math.BoundingBox3i")
public interface BoundingBox3i extends org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3ic, org.academy.internal.common.structure.sable.reflect.SableView {
    BoundingBox3ic EMPTY = SableReflection.field(BoundingBox3i.class, "EMPTY");
    static org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3i create(int a0, int a1, int a2, int a3, int a4, int a5) { return SableReflection.construct(BoundingBox3i.class, new Class<?>[]{int.class, int.class, int.class, int.class, int.class, int.class}, a0, a1, a2, a3, a4, a5); }
    static org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3i create(net.minecraft.core.BlockPos a0, net.minecraft.core.BlockPos a1) { return SableReflection.construct(BoundingBox3i.class, new Class<?>[]{net.minecraft.core.BlockPos.class, net.minecraft.core.BlockPos.class}, a0, a1); }
    static org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3i create(org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3ic a0) { return SableReflection.construct(BoundingBox3i.class, new Class<?>[]{org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3ic.class}, a0); }
    static org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3i create(net.minecraft.world.level.levelgen.structure.BoundingBox a0) { return SableReflection.construct(BoundingBox3i.class, new Class<?>[]{net.minecraft.world.level.levelgen.structure.BoundingBox.class}, a0); }
    static org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3i create() { return SableReflection.construct(BoundingBox3i.class, new Class<?>[]{}); }
    static org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3i create(org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d a0) { return SableReflection.construct(BoundingBox3i.class, new Class<?>[]{org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d.class}, a0); }
    static org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3i from(java.lang.Iterable<net.minecraft.core.BlockPos> a0) { return SableReflection.callStatic(BoundingBox3i.class, "from", new Class<?>[]{java.lang.Iterable.class}, a0); }
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3i set(org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d a0);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3i set(org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3ic a0);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3i set(int a0, int a1, int a2, int a3, int a4, int a5);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3i expand(int a0, int a1, int a2);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3i move(org.joml.Vector3ic a0);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3i move(int a0, int a1, int a2);
    int minX();
    int minY();
    int minZ();
    int maxX();
    int maxY();
    int maxZ();
    boolean equals(java.lang.Object a0);
    int hashCode();
    java.lang.String toString();
}
