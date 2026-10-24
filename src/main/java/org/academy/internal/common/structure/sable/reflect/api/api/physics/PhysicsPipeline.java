package org.academy.internal.common.structure.sable.reflect.api.api.physics;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.api.physics.PhysicsPipeline")
public interface PhysicsPipeline extends org.academy.internal.common.structure.sable.reflect.SableView {
    void init(org.joml.Vector3dc a0, double a1);
    void tick();
    void add(org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel a0, org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3dc a1);
    void remove(org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel a0);
    void add(org.academy.internal.common.structure.sable.reflect.api.api.sublevel.KinematicContraption a0);
    void remove(org.academy.internal.common.structure.sable.reflect.api.api.sublevel.KinematicContraption a0);
    org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3d readPose(org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel a0, org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3d a1);
    org.academy.internal.common.structure.sable.reflect.api.api.physics.object.box.BoxHandle addBox(org.academy.internal.common.structure.sable.reflect.api.api.physics.object.box.BoxPhysicsObject a0);
    void handleChunkSectionAddition(net.minecraft.world.level.chunk.LevelChunkSection a0, int a1, int a2, int a3, boolean a4);
    void teleport(org.academy.internal.common.structure.sable.reflect.api.api.physics.PhysicsPipelineBody a0, org.joml.Vector3dc a1, org.joml.Quaterniondc a2);
    void applyImpulse(org.academy.internal.common.structure.sable.reflect.api.api.physics.PhysicsPipelineBody a0, org.joml.Vector3dc a1, org.joml.Vector3dc a2);
    void addLinearAndAngularVelocity(org.academy.internal.common.structure.sable.reflect.api.api.physics.PhysicsPipelineBody a0, org.joml.Vector3dc a1, org.joml.Vector3dc a2);
    org.joml.Vector3d getLinearVelocity(org.academy.internal.common.structure.sable.reflect.api.api.physics.PhysicsPipelineBody a0, org.joml.Vector3d a1);
    org.joml.Vector3d getAngularVelocity(org.academy.internal.common.structure.sable.reflect.api.api.physics.PhysicsPipelineBody a0, org.joml.Vector3d a1);
    <T extends org.academy.internal.common.structure.sable.reflect.api.api.physics.constraint.PhysicsConstraintHandle> T addConstraint(org.academy.internal.common.structure.sable.reflect.api.api.physics.PhysicsPipelineBody a0, org.academy.internal.common.structure.sable.reflect.api.api.physics.PhysicsPipelineBody a1, org.academy.internal.common.structure.sable.reflect.api.api.physics.constraint.PhysicsConstraintConfiguration<T> a2);
}
