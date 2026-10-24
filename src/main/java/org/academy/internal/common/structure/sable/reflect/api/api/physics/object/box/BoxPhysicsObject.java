package org.academy.internal.common.structure.sable.reflect.api.api.physics.object.box;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.api.physics.object.box.BoxPhysicsObject")
public interface BoxPhysicsObject extends org.academy.internal.common.structure.sable.reflect.api.api.physics.object.ArbitraryPhysicsObject, org.academy.internal.common.structure.sable.reflect.api.api.physics.PhysicsPipelineBody, org.academy.internal.common.structure.sable.reflect.SableView {
    void getBoundingBox(org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3d a0);
    void updatePose();
    org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3dc getPose();
    double getMass();
    boolean isActive();
    int getRuntimeId();
    org.academy.internal.common.structure.sable.reflect.api.api.physics.mass.MassData getMassTracker();
    boolean isRemoved();
}
