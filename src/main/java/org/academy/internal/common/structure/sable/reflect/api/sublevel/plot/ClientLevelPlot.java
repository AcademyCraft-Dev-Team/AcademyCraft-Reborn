package org.academy.internal.common.structure.sable.reflect.api.sublevel.plot;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.plot.ClientLevelPlot")
public interface ClientLevelPlot extends org.academy.internal.common.structure.sable.reflect.api.sublevel.plot.LevelPlot, org.academy.internal.common.structure.sable.reflect.SableView {
    net.minecraft.world.level.lighting.LevelLightEngine getLightEngine();
    org.academy.internal.common.structure.sable.reflect.api.sublevel.ClientSubLevel getSubLevel();
}
