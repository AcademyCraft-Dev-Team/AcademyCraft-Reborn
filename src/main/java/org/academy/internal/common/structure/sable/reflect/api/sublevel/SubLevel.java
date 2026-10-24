package org.academy.internal.common.structure.sable.reflect.api.sublevel;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.SubLevel")
public interface SubLevel extends org.academy.internal.common.structure.sable.reflect.api.companion.SubLevelAccess, org.academy.internal.common.structure.sable.reflect.SableView {
    void updateLastPose();
    void tick();
    void updateBoundingBox();
    net.minecraft.world.level.Level getLevel();
    org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3d logicalPose();
    org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3dc lastPose();
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3dc boundingBox();
    org.academy.internal.common.structure.sable.reflect.api.sublevel.plot.LevelPlot getPlot();
    boolean isRemoved();
    void markRemoved();
    java.util.UUID getUniqueId();
    java.lang.String getName();
    java.lang.String toString();
}
