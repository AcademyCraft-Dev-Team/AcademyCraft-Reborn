package org.academy.internal.common.structure.sable.reflect.api.companion.math;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.companion.math.BoundingBox3dc")
public interface BoundingBox3dc extends org.academy.internal.common.structure.sable.reflect.SableView {
    boolean intersects(org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3dc a0);
    boolean intersects(net.minecraft.world.phys.AABB a0);
    boolean intersects(double a0, double a1, double a2, double a3, double a4, double a5);
    boolean contains(org.joml.Vector3dc a0);
    boolean contains(double a0, double a1, double a2);
    double minX();
    double minY();
    double minZ();
    double maxX();
    double maxY();
    double maxZ();
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d expand(double a0, org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d a1);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d expand(double a0, double a1, double a2, org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d a3);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d move(double a0, double a1, double a2, org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d a3);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d transform(org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3dc a0, org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d a1);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d transform(org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3dc a0, org.joml.Matrix4d a1, org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d a2);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d transform(org.joml.Matrix4dc a0, org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d a1);
    org.joml.Vector3d center();
    org.joml.Vector3d center(org.joml.Vector3d a0);
    org.joml.Vector3d size();
    org.joml.Vector3d size(org.joml.Vector3d a0);
    double width();
    double height();
    double length();
    double volume();
}
