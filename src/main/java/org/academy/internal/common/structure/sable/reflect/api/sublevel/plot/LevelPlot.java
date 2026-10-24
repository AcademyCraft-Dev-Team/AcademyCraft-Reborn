package org.academy.internal.common.structure.sable.reflect.api.sublevel.plot;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.plot.LevelPlot")
public interface LevelPlot extends org.academy.internal.common.structure.sable.reflect.SableView {
    @org.academy.internal.common.structure.sable.reflect.SableField
    net.minecraft.world.level.ChunkPos plotPos();
    void tick();
    org.academy.internal.common.structure.sable.reflect.api.sublevel.plot.EmbeddedPlotLevelAccessor getEmbeddedLevelAccessor();
    net.minecraft.core.BlockPos getCenterBlock();
    net.minecraft.world.level.lighting.LevelLightEngine getLightEngine();
    void newEmptyChunk(net.minecraft.world.level.ChunkPos a0);
    org.academy.internal.common.structure.sable.reflect.api.sublevel.SubLevel getSubLevel();
    boolean contains(double a0, double a1);
    boolean contains(net.minecraft.world.phys.Vec3 a0);
    boolean contains(org.joml.Vector3dc a0);
    boolean contains(net.minecraft.world.level.ChunkPos a0);
    net.minecraft.world.level.ChunkPos toLocal(net.minecraft.world.level.ChunkPos a0);
    org.academy.internal.common.structure.sable.reflect.api.sublevel.plot.PlotChunkHolder getChunkHolder(net.minecraft.world.level.ChunkPos a0);
    net.minecraft.world.level.chunk.LevelChunk getChunk(net.minecraft.world.level.ChunkPos a0);
    net.minecraft.world.level.ChunkPos getCenterChunk();
    java.util.Collection<org.academy.internal.common.structure.sable.reflect.api.sublevel.plot.PlotChunkHolder> getLoadedChunks();
    void updateBoundingBox();
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3ic getBoundingBox();
    void setBoundingBox(org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3ic a0);
    java.lang.Iterable<org.academy.internal.common.structure.sable.reflect.api.api.block.BlockEntitySubLevelActor> getBlockEntityActors();
}
