package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilitySystem;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.Packets;
import org.academy.api.common.wireless.WirelessMaster;
import org.academy.api.common.wireless.WirelessNode;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.network.NetworkSystemServer;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public abstract class AbilityDeveloperBlockEntity extends BlockEntity implements WirelessNode {
    @Nullable
    public WirelessMaster wirelessMaster;
    public String name;
    public BlockPos mainPos;
    public int energyStored;

    public AbilityDeveloperBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @SuppressWarnings("resource")
    public static void intiServer() {
        NetworkSystemServer.registerC2SPacketHandler(
                Packets.C2S_LEARN_SKILL,
                (listener, packet) -> {
                    FriendlyByteBuf friendlyByteBuf = packet.friendlyByteBuf;
                    String name = friendlyByteBuf.readUtf();
                    BlockPos blockPos = friendlyByteBuf.readBlockPos();
                    Skill skill = AbilitySystem.SKILL_MAP.get(name);
                    if (skill == null) return;
                    BlockEntity blockEntity = listener.player.level().getBlockEntity(blockPos);
                    if (blockEntity instanceof AbilityDeveloperBlockEntity abilityDeveloperBlockEntity) {
                        long needEnergy = skill.level * 10000L;
                        if (abilityDeveloperBlockEntity.energyStored >= needEnergy) {
                            abilityDeveloperBlockEntity.energyStored -= skill.level * 10000;
                            AbilitySystemServer.addPlayerSkill(listener.player.getUUID(), name);
                            Set<String> skillList = AbilitySystemServer.getPlayerSkills(listener.player.getUUID());
                            for (String skillName : skillList) {
                                AcademyCraft.LOGGER.info(skillName);
                            }
                        }
                    } else {
                        AcademyCraft.LOGGER.info("Invalid server learn skill packet for {}", name);
                    }
                }
        );
    }

    public void setMainPos(BlockPos pos) {
        this.mainPos = pos;
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public boolean isMain() {
        return this.getBlockState().getValue(AbilityDeveloperBlock.TYPE)
                .equals(AbilityDeveloperBlock.MultiBlockType.MAIN);
    }

    @Override
    public @Nullable WirelessMaster getWirelessMaster() {
        return wirelessMaster;
    }

    @Override
    public String getName() {
        return "AbilityDeveloperBlockEntity: " + mainPos;
    }

    @Override
    public int getEnergyStorage() {
        return energyStored;
    }

    @Override
    public void setEnergyStorage(int energyStorage) {
        this.energyStored = energyStorage;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (mainPos != null) {
            tag.putInt("mainPosX", mainPos.getX());
            tag.putInt("mainPosY", mainPos.getY());
            tag.putInt("mainPosZ", mainPos.getZ());
        }
        if (isMain()) {
            tag.putInt("energyStored", energyStored);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (isMain()) {
            energyStored = tag.getInt("energyStored");
        }
        mainPos = new BlockPos(
                tag.getInt("mainPosX"),
                tag.getInt("mainPosY"),
                tag.getInt("mainPosZ")
        );
    }
}