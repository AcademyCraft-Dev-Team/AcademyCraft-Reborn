package org.academy.internal.common.structure.sable.reflect.api.sublevel.plot;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor")
public interface EmbeddedPlotLevelAccessor extends org.academy.internal.common.structure.sable.reflect.SableView {
    boolean setBlock(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state, int flags);
    net.minecraft.world.level.lighting.LevelLightEngine getLightEngine();
    net.minecraft.world.level.border.WorldBorder getWorldBorder();
    net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(net.minecraft.core.BlockPos a0);
    net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.core.BlockPos a0);
    net.minecraft.world.level.material.FluidState getFluidState(net.minecraft.core.BlockPos a0);
    java.util.List<net.minecraft.world.entity.Entity> getEntities(net.minecraft.world.entity.Entity a0, net.minecraft.world.phys.AABB a1, java.util.function.Predicate<? super net.minecraft.world.entity.Entity> a2);
    <T extends net.minecraft.world.entity.Entity> java.util.List<T> getEntities(net.minecraft.world.level.entity.EntityTypeTest<net.minecraft.world.entity.Entity, T> a0, net.minecraft.world.phys.AABB a1, java.util.function.Predicate<? super T> a2);
    java.util.List<? extends net.minecraft.world.entity.player.Player> players();
    net.minecraft.world.level.chunk.ChunkAccess getChunk(int a0, int a1, net.minecraft.world.level.chunk.status.ChunkStatus a2, boolean a3);
    long nextSubTickCount();
    net.minecraft.world.ticks.LevelTickAccess<net.minecraft.world.level.block.Block> getBlockTicks();
    net.minecraft.world.ticks.LevelTickAccess<net.minecraft.world.level.material.Fluid> getFluidTicks();
    net.minecraft.world.level.storage.LevelData getLevelData();
    net.minecraft.server.MinecraftServer getServer();
    net.minecraft.world.level.chunk.ChunkSource getChunkSource();
    boolean hasChunk(int a0, int a1);
    net.minecraft.util.RandomSource getRandom();
    void playSound(net.minecraft.world.entity.Entity a0, net.minecraft.core.BlockPos a1, net.minecraft.sounds.SoundEvent a2, net.minecraft.sounds.SoundSource a3, float a4, float a5);
    void addParticle(net.minecraft.core.particles.ParticleOptions a0, double a1, double a2, double a3, double a4, double a5, double a6);
    void levelEvent(net.minecraft.world.entity.Entity a0, int a1, net.minecraft.core.BlockPos a2, int a3);
    void gameEvent(net.minecraft.core.Holder<net.minecraft.world.level.gameevent.GameEvent> a0, net.minecraft.world.phys.Vec3 a1, net.minecraft.world.level.gameevent.GameEvent.Context a2);
    int getHeight(net.minecraft.world.level.levelgen.Heightmap.Types a0, int a1, int a2);
    int getSkyDarken();
    net.minecraft.world.level.biome.BiomeManager getBiomeManager();
    boolean isClientSide();
    int getSeaLevel();
    net.minecraft.world.level.dimension.DimensionType dimensionType();
    net.minecraft.core.RegistryAccess registryAccess();
    net.minecraft.world.flag.FeatureFlagSet enabledFeatures();
    boolean setBlock(net.minecraft.core.BlockPos a0, net.minecraft.world.level.block.state.BlockState a1, int a2, int a3);
    boolean removeBlock(net.minecraft.core.BlockPos a0, boolean a1);
    boolean destroyBlock(net.minecraft.core.BlockPos a0, boolean a1, net.minecraft.world.entity.Entity a2, int a3);
    net.minecraft.server.level.ServerLevel getLevel();
    net.minecraft.world.attribute.EnvironmentAttributeReader environmentAttributes();
}
