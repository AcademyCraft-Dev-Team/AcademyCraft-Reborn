package org.academy.internal.common.structure.sable.reflect.api.api.physics.handle;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle")
public interface RigidBodyHandle extends org.academy.internal.common.structure.sable.reflect.SableView {
    static org.academy.internal.common.structure.sable.reflect.api.api.physics.handle.RigidBodyHandle of(net.minecraft.server.level.ServerLevel a0, org.academy.internal.common.structure.sable.reflect.api.api.physics.PhysicsPipelineBody a1) { return SableReflection.callStatic(RigidBodyHandle.class, "of", new Class<?>[]{net.minecraft.server.level.ServerLevel.class, org.academy.internal.common.structure.sable.reflect.api.api.physics.PhysicsPipelineBody.class}, a0, a1); }
    static org.academy.internal.common.structure.sable.reflect.api.api.physics.handle.RigidBodyHandle of(org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel a0) { return SableReflection.callStatic(RigidBodyHandle.class, "of", new Class<?>[]{org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel.class}, a0); }
    void applyImpulseAtPoint(org.joml.Vector3dc a0, org.joml.Vector3dc a1);
    void applyImpulseAtPoint(net.minecraft.world.phys.Vec3 a0, net.minecraft.world.phys.Vec3 a1);
    org.joml.Vector3dc getLinearVelocity();
    org.joml.Vector3dc getAngularVelocity();
    org.joml.Vector3d getLinearVelocity(org.joml.Vector3d a0);
    org.joml.Vector3d getAngularVelocity(org.joml.Vector3d a0);
    void addLinearAndAngularVelocity(org.joml.Vector3dc a0, org.joml.Vector3dc a1);
    void teleport(org.joml.Vector3dc a0, org.joml.Quaterniondc a1);
    boolean isValid();
}
