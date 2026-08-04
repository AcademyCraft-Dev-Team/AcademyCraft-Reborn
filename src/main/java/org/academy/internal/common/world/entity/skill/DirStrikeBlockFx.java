package org.academy.internal.common.world.entity.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.internal.common.world.entity.RenderOnlyEntity;

public final class DirStrikeBlockFx extends RenderOnlyEntity {
    private static final EntityDataAccessor<BlockState> BLOCK_STATE = SynchedEntityData.defineId(
            DirStrikeBlockFx.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<BlockPos> START_POS = SynchedEntityData.defineId(
            DirStrikeBlockFx.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> DURATION = SynchedEntityData.defineId(
            DirStrikeBlockFx.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DELAY = SynchedEntityData.defineId(
            DirStrikeBlockFx.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HOLD_TICKS = SynchedEntityData.defineId(
            DirStrikeBlockFx.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> PEAK_HEIGHT = SynchedEntityData.defineId(
            DirStrikeBlockFx.class, EntityDataSerializers.FLOAT);

    public DirStrikeBlockFx(EntityType<?> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public DirStrikeBlockFx(EntityType<?> entityType, Level level, BlockPos pos, BlockState blockState,
                            int delay, int duration, int holdTicks, float peakHeight) {
        this(entityType, level);
        setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        entityData.set(START_POS, pos);
        entityData.set(BLOCK_STATE, blockState);
        entityData.set(DELAY, Math.max(0, delay));
        entityData.set(DURATION, Math.max(1, duration));
        entityData.set(HOLD_TICKS, Math.max(0, holdTicks));
        entityData.set(PEAK_HEIGHT, Math.max(0.05f, peakHeight));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BLOCK_STATE, Blocks.AIR.defaultBlockState());
        builder.define(START_POS, BlockPos.ZERO);
        builder.define(DURATION, 9);
        builder.define(DELAY, 0);
        builder.define(HOLD_TICKS, 20);
        builder.define(PEAK_HEIGHT, 0.45f);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > getDelay() + getDuration() + getHoldTicks() + 2) discard();
    }

    public BlockState getBlockState() {
        return entityData.get(BLOCK_STATE);
    }

    public BlockPos getStartPos() {
        return entityData.get(START_POS);
    }

    public int getDuration() {
        return entityData.get(DURATION);
    }

    public int getDelay() {
        return entityData.get(DELAY);
    }

    public int getHoldTicks() {
        return entityData.get(HOLD_TICKS);
    }

    public float getPeakHeight() {
        return entityData.get(PEAK_HEIGHT);
    }

    public float getActiveTick(float partialTick) {
        return Math.max(0.0f, tickCount + partialTick - getDelay());
    }

    public boolean isActive(float partialTick) {
        return tickCount + partialTick >= getDelay();
    }
}
