package org.academy.internal.common.structure.sable.reflect.api.companion.math;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.companion.math.Pose3dc")
public interface Pose3dc extends org.academy.internal.common.structure.sable.reflect.SableView {
    org.joml.Vector3dc position();
    org.joml.Quaterniondc orientation();
    org.joml.Vector3dc rotationPoint();
    org.joml.Vector3dc scale();
    org.joml.Vector3d transformPosition(org.joml.Vector3dc a0, org.joml.Vector3d a1);
    net.minecraft.world.phys.Vec3 transformPosition(net.minecraft.world.phys.Vec3 a0);
    org.joml.Vector3d transformNormal(org.joml.Vector3dc a0, org.joml.Vector3d a1);
    org.joml.Vector3d transformPosition(org.joml.Vector3d a0);
    org.joml.Vector3d transformNormal(org.joml.Vector3d a0);
    net.minecraft.world.phys.Vec3 transformNormal(net.minecraft.world.phys.Vec3 a0);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3d lerp(org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3dc a0, double a1, org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3d a2);
}
