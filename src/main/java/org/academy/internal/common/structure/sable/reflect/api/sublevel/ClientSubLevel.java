package org.academy.internal.common.structure.sable.reflect.api.sublevel;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.ClientSubLevel")
public interface ClientSubLevel extends org.academy.internal.common.structure.sable.reflect.api.sublevel.SubLevel, org.academy.internal.common.structure.sable.reflect.api.companion.ClientSubLevelAccess, org.academy.internal.common.structure.sable.reflect.SableView {
    void tick();
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3dc boundingBox();
    org.academy.internal.common.structure.sable.reflect.api.sublevel.render.SubLevelRenderData getRenderData();
    net.minecraft.client.multiplayer.ClientLevel getLevel();
    org.academy.internal.common.structure.sable.reflect.api.sublevel.plot.ClientLevelPlot getPlot();
    org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3dc renderPose();
    org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3dc renderPose(float a0);
    java.lang.String toString();
}
