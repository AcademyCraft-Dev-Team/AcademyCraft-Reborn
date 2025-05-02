package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.internal.common.world.level.block.MultiBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class MultiBlockEntity extends BlockEntity {
    public BlockPos mainPos;

    public MultiBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        if (isMain()){
            setMainPos(pos);
        }
    }

    public void setMainPos(BlockPos pos) {
        mainPos = pos;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public boolean isMain() {
        BlockState state = this.getBlockState();
        return state.getValue(MultiBlock.TYPE).equals(MultiBlock.MultiBlockType.MAIN);
    }

    @Nullable
    public MultiBlockEntity getMain() {
        if (isMain()) {
            return this;
        } else {
            if (level != null) {
                BlockEntity blockEntity = level.getBlockEntity(mainPos);
                if (blockEntity instanceof MultiBlockEntity multiBlockEntity) {
                    return multiBlockEntity;
                }
            }
            return null;
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (mainPos != null) {
            tag.putLong("main_pos", mainPos.asLong());
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        mainPos = BlockPos.of(tag.getLong("main_pos"));
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}