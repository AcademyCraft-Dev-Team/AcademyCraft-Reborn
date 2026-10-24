package org.academy.internal.common.structure.sable.reflect.api.api.sublevel;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer")
public interface ServerSubLevelContainer extends org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer, org.academy.internal.common.structure.sable.reflect.SableView {
    void initialize();
    void tick();
    org.academy.internal.common.structure.sable.reflect.api.sublevel.system.SubLevelPhysicsSystem physicsSystem();
    org.academy.internal.common.structure.sable.reflect.api.sublevel.system.SubLevelTrackingSystem trackingSystem();
    void removeSubLevel(int a0, int a1, org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.SubLevelRemovalReason a2);
    org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.holding.SubLevelHoldingChunkMap getHoldingChunkMap();
    java.util.List<org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel> getAllSubLevels();
    net.minecraft.server.level.ServerLevel getLevel();
    void close();
}
