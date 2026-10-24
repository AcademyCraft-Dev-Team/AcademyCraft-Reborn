package org.academy.internal.common.structure.sable.reflect.api.sublevel.plot;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot")
public interface ServerLevelPlot extends org.academy.internal.common.structure.sable.reflect.api.sublevel.plot.LevelPlot, org.academy.internal.common.structure.sable.reflect.SableView {
    java.util.Collection<org.academy.internal.common.structure.sable.reflect.api.api.sublevel.KinematicContraption> getContraptions();
    void tick();
    net.minecraft.world.level.lighting.LevelLightEngine getLightEngine();
    void destroyAllBlocks();
    net.minecraft.nbt.CompoundTag save();
    void load(net.minecraft.nbt.CompoundTag a0);
    org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel getSubLevel();
}
