package org.academy.internal.common.structure.upgrade.lifecycle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** Whole proposed placement image, with loaded-only reads for survival/support dependencies. */
final class RestorationPlacementView implements LevelReader {
    private final ServerLevel host;
    private final Map<BlockPos, BlockState> placements;
    private int reads;

    private RestorationPlacementView(ServerLevel host, Map<BlockPos, BlockState> placements) {
        this.host = host;
        this.placements = Map.copyOf(placements);
    }

    static boolean canSurvive(ServerLevel host, Map<BlockPos, BlockState> placements) {
        var view = new RestorationPlacementView(host, placements);
        try {
            for (var entry : placements.entrySet()) if (!entry.getValue().canSurvive(view, entry.getKey())) return false;
            return true;
        } catch (RuntimeException unavailable) { return false; }
    }

    static boolean collidableObstacle(ServerLevel host, BlockPos position) {
        var view = new RestorationPlacementView(host, Map.of());
        try { return !view.getBlockState(position).getCollisionShape(view, position).isEmpty(); }
        catch (RuntimeException unavailable) { return false; }
    }

    private LevelChunk loaded(int x, int z) {
        if (++reads > 16384) throw new IllegalStateException("Restoration dependency budget");
        var chunk = host.getChunkSource().getChunkNow(x, z);
        if (chunk == null) throw new IllegalStateException("Restoration dependency is unloaded");
        return chunk;
    }
    @Override public BlockState getBlockState(BlockPos pos) {
        var proposed = placements.get(pos);
        return proposed == null ? loaded(pos.getX() >> 4, pos.getZ() >> 4).getBlockState(pos) : proposed;
    }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return placements.containsKey(pos) ? null : loaded(pos.getX() >> 4, pos.getZ() >> 4).getBlockEntities().get(pos);
    }
    @Override public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean load) { return loaded(x, z); }
    @Override public boolean hasChunk(int x, int z) { return host.getChunkSource().getChunkNow(x, z) != null; }
    @Override public int getHeight(Heightmap.Types type, int x, int z) { return loaded(x >> 4, z >> 4).getHeight(type, x & 15, z & 15) + 1; }
    @Override public int getSkyDarken() { return host.getSkyDarken(); }
    @Override public BiomeManager getBiomeManager() { return host.getBiomeManager(); }
    @Override public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) { return loaded(x >> 2, z >> 2).getNoiseBiome(x, y, z); }
    @Override public boolean isClientSide() { return false; }
    @Override public int getSeaLevel() { return host.getSeaLevel(); }
    @Override public DimensionType dimensionType() { return host.dimensionType(); }
    @Override public RegistryAccess registryAccess() { return host.registryAccess(); }
    @Override public FeatureFlagSet enabledFeatures() { return host.enabledFeatures(); }
    @Override public EnvironmentAttributeReader environmentAttributes() { return host.environmentAttributes(); }
    @Override public LevelLightEngine getLightEngine() { return host.getLightEngine(); }
    @Override public WorldBorder getWorldBorder() { return host.getWorldBorder(); }
    @Override public RestorationPlacementView getChunkForCollisions(int x, int z) { loaded(x, z); return this; }
    @Override public List<VoxelShape> getEntityCollisions(@Nullable Entity source, AABB area) { return host.getEntityCollisions(source, area); }
}
