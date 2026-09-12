package org.academy.internal.common.world.level.block.entity;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Coalesces energy-only packets while storage mutations and disk dirty marking stay immediate. */
final class EnergyUpdateThrottle {
    private static final int INTERVAL_TICKS = 5;
    private boolean dirty;
    private boolean hasSent;
    private int lastSentEnergy;
    private long lastSentTick;

    void markChanged() {
        dirty = true;
    }

    void flush(BlockEntity entity, int energy) {
        var level = entity.getLevel();
        if (level == null || level.isClientSide() || !shouldSync(level.getGameTime(), energy)) return;
        level.sendBlockUpdated(entity.getBlockPos(), entity.getBlockState(), entity.getBlockState(),
                Block.UPDATE_CLIENTS);
    }

    boolean shouldSync(long tick, int energy) {
        if (!dirty) return false;
        if (hasSent && energy == lastSentEnergy) {
            dirty = false;
            return false;
        }
        if (hasSent && tick >= lastSentTick && tick - lastSentTick < INTERVAL_TICKS) return false;
        dirty = false;
        hasSent = true;
        lastSentEnergy = energy;
        lastSentTick = tick;
        return true;
    }
}
