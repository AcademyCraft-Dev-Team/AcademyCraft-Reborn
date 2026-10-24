package org.academy.internal.common.structure.sable.reflect.api.api.sublevel;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.api.sublevel.SubLevelContainer")
public interface SubLevelContainer extends org.academy.internal.common.structure.sable.reflect.SableView {
    static org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer getContainer(net.minecraft.world.level.Level a0) { return SableReflection.callStatic(SubLevelContainer.class, "getContainer", new Class<?>[]{net.minecraft.world.level.Level.class}, a0); }
    static org.academy.internal.common.structure.sable.reflect.api.api.sublevel.ServerSubLevelContainer getContainer(net.minecraft.server.level.ServerLevel a0) { return SableReflection.callStatic(SubLevelContainer.class, "getContainer", new Class<?>[]{net.minecraft.server.level.ServerLevel.class}, a0); }
    static org.academy.internal.common.structure.sable.reflect.api.api.sublevel.ClientSubLevelContainer getContainer(net.minecraft.client.multiplayer.ClientLevel a0) { return SableReflection.callStatic(SubLevelContainer.class, "getContainer", new Class<?>[]{net.minecraft.client.multiplayer.ClientLevel.class}, a0); }
    static org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer create(net.minecraft.world.level.Level a0, int a1, int a2, int a3, int a4) { return SableReflection.construct(SubLevelContainer.class, new Class<?>[]{net.minecraft.world.level.Level.class, int.class, int.class, int.class, int.class}, a0, a1, a2, a3, a4); }
    @org.academy.internal.common.structure.sable.reflect.SableField
    int originX();
    @org.academy.internal.common.structure.sable.reflect.SableField
    int originZ();
    void tick();
    void addObserver(org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelObserver a0);
    int getIndex(int a0, int a1);
    org.academy.internal.common.structure.sable.reflect.api.sublevel.SubLevel getSubLevel(int a0, int a1);
    org.academy.internal.common.structure.sable.reflect.api.sublevel.SubLevel allocateNewSubLevel(org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3d a0);
    org.academy.internal.common.structure.sable.reflect.api.sublevel.SubLevel allocateSubLevel(java.util.UUID a0, int a1, int a2, org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3d a3);
    net.minecraft.world.level.chunk.LevelChunk getChunk(net.minecraft.world.level.ChunkPos a0);
    org.academy.internal.common.structure.sable.reflect.api.sublevel.plot.PlotChunkHolder getChunkHolder(net.minecraft.world.level.ChunkPos a0);
    org.academy.internal.common.structure.sable.reflect.api.sublevel.plot.LevelPlot getPlot(int a0, int a1);
    org.academy.internal.common.structure.sable.reflect.api.sublevel.plot.LevelPlot getPlot(net.minecraft.world.level.ChunkPos a0);
    java.util.List<? extends org.academy.internal.common.structure.sable.reflect.api.sublevel.SubLevel> getAllSubLevels();
    net.minecraft.world.level.Level getLevel();
    int getLogSideLength();
    org.joml.Vector2i getOrigin();
    void removeSubLevel(int a0, int a1, org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.SubLevelRemovalReason a2);
    void removeSubLevel(org.academy.internal.common.structure.sable.reflect.api.sublevel.SubLevel a0, org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.SubLevelRemovalReason a1);
    org.academy.internal.common.structure.sable.reflect.api.sublevel.SubLevel getSubLevel(java.util.UUID a0);
    java.util.BitSet getOccupancy();
}
