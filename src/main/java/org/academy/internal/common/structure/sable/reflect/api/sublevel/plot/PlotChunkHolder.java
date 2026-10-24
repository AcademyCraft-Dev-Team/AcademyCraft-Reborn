package org.academy.internal.common.structure.sable.reflect.api.sublevel.plot;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder")
public interface PlotChunkHolder extends org.academy.internal.common.structure.sable.reflect.SableView {
    static org.academy.internal.common.structure.sable.reflect.api.sublevel.plot.PlotChunkHolder create(net.minecraft.world.level.Level a0, net.minecraft.world.level.ChunkPos a1, net.minecraft.world.level.lighting.LevelLightEngine a2, net.minecraft.world.level.chunk.LevelChunk a3) { return SableReflection.callStatic(PlotChunkHolder.class, "create", new Class<?>[]{net.minecraft.world.level.Level.class, net.minecraft.world.level.ChunkPos.class, net.minecraft.world.level.lighting.LevelLightEngine.class, net.minecraft.world.level.chunk.LevelChunk.class}, a0, a1, a2, a3); }
    boolean blockChanged(net.minecraft.core.BlockPos a0);
    net.minecraft.world.level.chunk.LevelChunk getChunk();
    org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3ic getBoundingBox();
    net.minecraft.world.level.chunk.LevelChunk getTickingChunk();
}
