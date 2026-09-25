package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.academy.internal.common.world.level.block.MultiBlock;
import org.jspecify.annotations.Nullable;

import static net.minecraft.world.level.block.Block.UPDATE_CLIENTS;
import static net.minecraft.world.level.block.Block.UPDATE_NEIGHBORS;

public abstract class MultiBlockEntity extends BlockEntity {
    @Nullable
    public BlockPos mainPos;

    public MultiBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        if (isMain()) {
            setMainPos(pos);
        }
    }

    public void setMainPos(BlockPos pos) {
        mainPos = pos;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(
                    getBlockPos(), getBlockState(), getBlockState(), UPDATE_NEIGHBORS | UPDATE_CLIENTS
            );
        }
    }

    public boolean isMain() {
        return getBlockState().getValue(MultiBlock.TYPE).equals(MultiBlock.MultiBlockType.MAIN);
    }

    @Nullable
    public MultiBlockEntity getMain() {
        if (isMain()) return this;
        if (level == null || mainPos == null) return null;
        BlockEntity main;
        if (level instanceof ServerLevel serverLevel) {
            var chunk = serverLevel.getChunkSource().getChunkNow(mainPos.getX() >> 4, mainPos.getZ() >> 4);
            main = chunk == null ? null : chunk.getBlockEntity(mainPos);
        } else {
            main = level.getBlockEntity(mainPos);
        }
        return main instanceof MultiBlockEntity multiBlockEntity ? multiBlockEntity : null;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (mainPos != null) {
            output.putLong("main_pos", mainPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        mainPos = BlockPos.of(input.getLong("main_pos").orElseThrow());
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
