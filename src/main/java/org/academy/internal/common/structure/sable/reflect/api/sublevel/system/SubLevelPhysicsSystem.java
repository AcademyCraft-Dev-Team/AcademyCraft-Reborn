package org.academy.internal.common.structure.sable.reflect.api.sublevel.system;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem")
public interface SubLevelPhysicsSystem extends org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelObserver, org.academy.internal.common.structure.sable.reflect.SableView {
    static org.academy.internal.common.structure.sable.reflect.api.sublevel.system.SubLevelPhysicsSystem get(net.minecraft.world.level.Level a0) { return SableReflection.callStatic(SubLevelPhysicsSystem.class, "get", new Class<?>[]{net.minecraft.world.level.Level.class}, a0); }
    static org.academy.internal.common.structure.sable.reflect.api.sublevel.system.SubLevelPhysicsSystem require(net.minecraft.world.level.Level a0) { return SableReflection.callStatic(SubLevelPhysicsSystem.class, "require", new Class<?>[]{net.minecraft.world.level.Level.class}, a0); }
    void initialize();
    void onSubLevelAdded(org.academy.internal.common.structure.sable.reflect.api.sublevel.SubLevel a0);
    void onSubLevelRemoved(org.academy.internal.common.structure.sable.reflect.api.sublevel.SubLevel a0, org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.SubLevelRemovalReason a1);
    void tick(org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer a0);
    void updatePose(org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel a0);
    org.academy.internal.common.structure.sable.reflect.api.api.physics.PhysicsPipeline getPipeline();
    org.academy.internal.common.structure.sable.reflect.api.api.physics.handle.RigidBodyHandle getPhysicsHandle(org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel a0);
    double getPartialPhysicsTick();
    boolean getPaused();
    org.academy.internal.common.structure.sable.reflect.api.physics.config.PhysicsConfigData getConfig();
    net.minecraft.server.level.ServerLevel getLevel();
}
