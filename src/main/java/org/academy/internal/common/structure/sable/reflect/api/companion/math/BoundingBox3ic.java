package org.academy.internal.common.structure.sable.reflect.api.companion.math;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.companion.math.BoundingBox3ic")
public interface BoundingBox3ic extends org.academy.internal.common.structure.sable.reflect.SableView {
    boolean intersects(org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3ic a0);
    boolean intersects(net.minecraft.world.level.levelgen.structure.BoundingBox a0);
    boolean intersects(int a0, int a1, int a2, int a3, int a4, int a5);
    boolean contains(org.joml.Vector3ic a0);
    boolean contains(int a0, int a1, int a2);
    boolean contains(org.joml.Vector3dc a0);
    int minX();
    int minY();
    int minZ();
    int maxX();
    int maxY();
    int maxZ();
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3i move(org.joml.Vector3ic a0, org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3i a1);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3i move(int a0, int a1, int a2, org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3i a3);
    org.joml.Vector3i center(org.joml.Vector3i a0);
    org.joml.Vector3i size(org.joml.Vector3i a0);
    int width();
    int height();
    int length();
    int volume();
}
