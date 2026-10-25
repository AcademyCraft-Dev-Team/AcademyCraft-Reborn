package org.academy.internal.common.structure.upgrade.damage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/** Limit arbitrary shape dependency reads without loading chunks or entering adjacent plots. */
final class DamageShapeView implements BlockGetter {
    private final @Nullable DamageDomain domain;
    private final ServerLevel level;
    private int reads;
    DamageShapeView(DamageDomain domain) { this(domain.storage(), domain); }
    DamageShapeView(ServerLevel level, @Nullable DamageDomain domain) { this.level = level; this.domain = domain; }
    private net.minecraft.world.level.chunk.LevelChunk chunk(BlockPos position) {
        if (++reads > 128 || level.isOutsideBuildHeight(position)) throw new IllegalStateException("damage_shape_budget");
        var chunk = domain == null ? level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) : domain.chunk(position);
        if (chunk == null) throw new IllegalStateException("damage_shape_unloaded");
        return chunk;
    }
    public BlockState getBlockState(BlockPos position) { return chunk(position).getBlockState(position); }
    public @Nullable BlockEntity getBlockEntity(BlockPos position) { return chunk(position).getBlockEntities().get(position); }
    public FluidState getFluidState(BlockPos position) { return getBlockState(position).getFluidState(); }
    public int getHeight() { return level.getHeight(); }
    public int getMinY() { return level.getMinY(); }
}
