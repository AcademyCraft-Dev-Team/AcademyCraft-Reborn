package org.academy.internal.common.structure.sable.reflect.api.companion.math;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.companion.math.BoundingBox3d")
public interface BoundingBox3d extends org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3dc, org.academy.internal.common.structure.sable.reflect.SableView {
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d set(org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3dc a0);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d set(net.minecraft.world.phys.AABB a0);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d set(double a0, double a1, double a2, double a3, double a4, double a5);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d expand(double a0);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d expand(double a0, double a1, double a2);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d move(double a0, double a1, double a2);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d transform(org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3dc a0);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d transform(org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3dc a0, org.joml.Matrix4d a1);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d transform(org.joml.Matrix4dc a0);
    double minX();
    double minY();
    double minZ();
    double maxX();
    double maxY();
    double maxZ();
}
