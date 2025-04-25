package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.internal.common.world.level.block.MultiBlock;
import org.jetbrains.annotations.NotNull;

public class MultiBlockEntity extends BlockEntity {
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

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (mainPos != null) {
            tag.putInt("main_pos_x", mainPos.getX());
            tag.putInt("main_pos_y", mainPos.getY());
            tag.putInt("main_pos_z", mainPos.getZ());
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains("main_pos_x")) {
            mainPos = new BlockPos(tag.getInt("main_pos_x"), tag.getInt("main_pos_y"), tag.getInt("main_pos_z"));
        }
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }
}