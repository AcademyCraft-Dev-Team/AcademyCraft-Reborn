package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.api.common.wireless.WirelessUser;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * @author cnlimiter
 */
public final class CatEngineBlockEntity extends BlockEntity implements WirelessUser {
    public int time;
    public float rot;
    public float oRot;
    public float tRot;
    public boolean enable = false;
    public float rH = 0;
    private BlockPos connectedNodePos = null;

    public CatEngineBlockEntity(BlockPos p_155229_, BlockState p_155230_) {
        super(BlockEntityTypes.CAT_ENGINE.get(), p_155229_, p_155230_);
    }

    public static void tickAnim(Level level, BlockPos blockPos, BlockState blockState, CatEngineBlockEntity e) {
        e.oRot = e.rot;
        var player = level.getNearestPlayer(blockPos.getX(), blockPos.getY(), blockPos.getZ(), 3, false);
        if (player != null) {
            var d0 = player.getX() - ((double) blockPos.getX() + 0.5D);
            var d1 = player.getZ() - ((double) blockPos.getZ() + 0.5D);
            e.tRot = (float) Mth.atan2(d1, d0);
        } else {
            e.tRot += 0.02F;
        }
        while (e.rot >= (float) Math.PI) {
            e.rot -= ((float) Math.PI * 2F);
        }

        while (e.rot < -(float) Math.PI) {
            e.rot += ((float) Math.PI * 2F);
        }

        while (e.tRot >= (float) Math.PI) {
            e.tRot -= ((float) Math.PI * 2F);
        }

        while (e.tRot < -(float) Math.PI) {
            e.tRot += ((float) Math.PI * 2F);
        }

        float f2;
        for (f2 = e.tRot - e.rot; f2 >= (float) Math.PI; f2 -= ((float) Math.PI * 2F)) {
        }

        while (f2 < -(float) Math.PI) {
            f2 += ((float) Math.PI * 2F);
        }

        e.rot += f2 * 0.4F;
        ++e.time;
    }

    @Override
    public @Nullable BlockPos getConnectedNodePosition() {
        return connectedNodePos;
    }

    @Override
    public void setConnectedNodePosition(@Nullable BlockPos nodePos) {
        if (!Objects.equals(connectedNodePos, nodePos)) {
            connectedNodePos = nodePos;
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return maxExtract;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return Integer.MAX_VALUE - 1;
    }

    @Override
    public int getMaxEnergyStorage() {
        return Integer.MAX_VALUE;
    }
}
