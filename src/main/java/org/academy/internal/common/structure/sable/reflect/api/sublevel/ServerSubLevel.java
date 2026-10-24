package org.academy.internal.common.structure.sable.reflect.api.sublevel;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.ServerSubLevel")
public interface ServerSubLevel extends org.academy.internal.common.structure.sable.reflect.api.sublevel.SubLevel, org.academy.internal.common.structure.sable.reflect.api.api.physics.PhysicsPipelineBody, org.academy.internal.common.structure.sable.reflect.SableView {
    java.util.Collection<java.util.UUID> getTrackingPlayers();
    int getRuntimeId();
    void tick();
    void updateMergedMassData(float a0);
    java.util.UUID getSplitFromSubLevel();
    net.minecraft.server.level.ServerLevel getLevel();
    org.academy.internal.common.structure.sable.reflect.api.sublevel.plot.ServerLevelPlot getPlot();
    org.academy.internal.common.structure.sable.reflect.api.api.physics.mass.MassData getMassTracker();
    void buildMassTracker();
    org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.holding.GlobalSavedSubLevelPointer getLastSerializationPointer();
    java.lang.String toString();
}
