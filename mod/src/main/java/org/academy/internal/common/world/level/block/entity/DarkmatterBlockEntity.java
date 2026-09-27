package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.academy.api.common.ability.darkmatter.DarkmatterBlockProfile;

/**
 * Stores physical parameters that cannot be represented economically as block-state variants.
 */
public final class DarkmatterBlockEntity extends BlockEntity {
    private DarkmatterBlockProfile profile = DarkmatterBlockProfile.DEFAULT;

    public DarkmatterBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityTypes.DARKMATTER_BLOCK.get(), pos, state);
    }

    public DarkmatterBlockProfile profile() {
        return profile;
    }

    public void setProfile(DarkmatterBlockProfile profile) {
        this.profile = profile == null ? DarkmatterBlockProfile.DEFAULT : profile;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("hardness_milli", Math.round(profile.hardness() * 1_000.0f));
        output.putInt("explosion_resistance_milli",
                Math.round(profile.explosionResistance() * 1_000.0f));
        output.putBoolean("gravity", profile.gravity());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        profile = new DarkmatterBlockProfile(
                input.getIntOr("hardness_milli", 5_000) / 1_000.0f,
                input.getIntOr("explosion_resistance_milli", 30_000) / 1_000.0f,
                input.getBooleanOr("gravity", false));
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
