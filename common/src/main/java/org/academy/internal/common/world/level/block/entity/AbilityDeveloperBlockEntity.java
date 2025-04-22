package org.academy.internal.common.world.level.block.entity;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilitySystem;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.Packets;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.network.NetworkSystemServer;
import org.academy.internal.client.models.AbilityDeveloperBlockEntityModel;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

public abstract class AbilityDeveloperBlockEntity extends BlockEntity implements WirelessUser {
    public String name;
    public BlockPos mainPos;
    public int energyStored;
    public int ticks;
    public final AnimationState openState = new AnimationState();
    public final AnimationState closingState = new AnimationState();
    public final AnimationState standState = new AnimationState();
    public final AnimationState liedownState = new AnimationState();
    public boolean isOpen = false;
    private BlockPos connectedNodePos = null;


    public AbilityDeveloperBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
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
                    listener.player.level();
                    BlockEntity blockEntity = listener.player.level().getBlockEntity(blockPos);
                    if (blockEntity instanceof AbilityDeveloperBlockEntity abilityDeveloperBlockEntity) {
                        long needEnergy = skill.level * 10000L;
                        if (abilityDeveloperBlockEntity.getEnergyStorage() >= needEnergy) {
                            abilityDeveloperBlockEntity.setEnergyStorage(abilityDeveloperBlockEntity.getEnergyStorage() - (int) needEnergy);
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
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public void setOpen(boolean open) {
        boolean previousIsOpen = this.isOpen;
        this.isOpen = open;
        if (level != null && level.isClientSide) {
            this.openState.animateWhen(this.isOpen, this.ticks);
            this.closingState.animateWhen(!this.isOpen, this.ticks);
            AnimationState currentAnimationState = previousIsOpen ? openState : closingState;
            AnimationState targetAnimationState = open ? openState : closingState;
            AnimationDefinition targetAnimationDefinition = open ? AbilityDeveloperBlockEntityModel.open : AbilityDeveloperBlockEntityModel.close;
            long elapsedMillis = currentAnimationState.getAccumulatedTime();
            currentAnimationState.stop();
            if (elapsedMillis > 0) {
                float elapsedSeconds = elapsedMillis / 1000.0f;
                float totalDuration = targetAnimationDefinition.lengthInSeconds();
                float targetStartSeconds = Math.max(0.0f, Math.min(totalDuration, totalDuration - elapsedSeconds));
                long targetElapsedTicks = (long)(targetStartSeconds * 20.0f);
                long adjustedStartTick = this.ticks - targetElapsedTicks;
                targetAnimationState.start((int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, adjustedStartTick)));
            } else {
                targetAnimationState.start(this.ticks);
            }
        } else if (level != null) {
            this.openState.stop();
            this.closingState.stop();
        }
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public boolean isMain() {
        BlockState state = this.getBlockState();
        return state.getValue(AbilityDeveloperBlock.TYPE).equals(AbilityDeveloperBlock.MultiBlockType.MAIN);
    }

    @Override
    public Level getOwningLevel() {
        return this.level;
    }

    @Override
    public BlockPos getPosition() {
        return this.worldPosition;
    }

    @Nullable
    @Override
    public BlockPos getConnectedNodePosition() {
        if (!isMain() && level != null && mainPos != null) {
            BlockEntity mainBE = level.getBlockEntity(mainPos);
            if (mainBE instanceof AbilityDeveloperBlockEntity mainDevBE) {
                return mainDevBE.getConnectedNodePosition();
            }
            return null;
        }
        return this.connectedNodePos;
    }

    @Override
    public void setConnectedNodePosition(@Nullable BlockPos nodePos) {
        if (!isMain() && level != null && mainPos != null) {
            BlockEntity mainBE = level.getBlockEntity(mainPos);
            if (mainBE instanceof AbilityDeveloperBlockEntity mainDevBE) {
                mainDevBE.setConnectedNodePosition(nodePos);
            }
            return;
        }
        if (!Objects.equals(this.connectedNodePos, nodePos)) {
            this.connectedNodePos = nodePos;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    }

    @Override
    public double extractEnergy(double maxExtract, boolean simulate) {
        return 0.0;
    }

    @Override
    public double receiveEnergy(double maxReceive, boolean simulate) {
        if (!isMain() && level != null && mainPos != null) {
            BlockEntity mainBE = level.getBlockEntity(mainPos);
            if (mainBE instanceof WirelessUser mainUser) {
                return mainUser.receiveEnergy(maxReceive, simulate);
            } else {
                return 0.0;
            }
        }
        double maxEnergyCanStore = getMaxEnergyStorage_internal();
        double energyStoredDouble = getEnergyStorage_internal();
        double maxCanReceive = Math.max(0.0, maxEnergyCanStore - energyStoredDouble);
        double energyToReceive = Math.min(maxReceive, maxCanReceive);
        if (energyToReceive <= 0.0) {
            return 0.0;
        }
        if (!simulate) {
            setEnergyStorage(getEnergyStorage_internal() + (int) Math.floor(energyToReceive));
        }
        return energyToReceive;
    }

    @Override
    public double getEnergyStored() {
        return getEnergyStorage_internal();
    }

    @Override
    public double getMaxEnergyStorage() {
        return getMaxEnergyStorage_internal();
    }

    private int getEnergyStorage_internal() {
        if (!isMain() && level != null && mainPos != null) {
            BlockEntity mainBE = level.getBlockEntity(mainPos);
            if (mainBE instanceof AbilityDeveloperBlockEntity mainDevBE) {
                return mainDevBE.getEnergyStorage_internal();
            }
            return 0;
        }
        return this.energyStored;
    }

    private double getMaxEnergyStorage_internal() {
        return 1_000_000.0;
    }

    public int getEnergyStorage() {
        return getEnergyStorage_internal();
    }

    public void setEnergyStorage(int energyStorage) {
        if (!isMain() && level != null && mainPos != null) {
            BlockEntity mainBE = level.getBlockEntity(mainPos);
            if (mainBE instanceof AbilityDeveloperBlockEntity mainDevBE) {
                mainDevBE.setEnergyStorage(energyStorage);
            }
            return;
        }
        int oldEnergy = this.energyStored;
        int maxInt = (int) Math.min(Integer.MAX_VALUE, getMaxEnergyStorage_internal());
        this.energyStored = Math.max(0, Math.min(energyStorage, maxInt));
        if (oldEnergy != this.energyStored) {
            setChanged();
        }
    }


    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (mainPos != null) {
            tag.putInt("main_pos_x", mainPos.getX());
            tag.putInt("main_pos_y", mainPos.getY());
            tag.putInt("main_pos_z", mainPos.getZ());
        }
        if (isMain()) {
            tag.putInt("energy_stored", energyStored);
            tag.putBoolean("is_open", isOpen);
            if (this.connectedNodePos != null) {
                tag.put("connected_node_pos", NbtUtils.writeBlockPos(this.connectedNodePos));
            }
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains("main_pos_x")) {
            mainPos = new BlockPos(tag.getInt("main_pos_x"), tag.getInt("main_pos_y"), tag.getInt("main_pos_z"));
        } else {
            this.mainPos = null;
        }

        if (isMain()) {
            energyStored = tag.getInt("energy_stored");
            if (tag.contains("connected_node_pos", Tag.TAG_COMPOUND)) {
                this.connectedNodePos = NbtUtils.readBlockPos(tag.getCompound("connected_node_pos"));
            } else {
                this.connectedNodePos = null;
            }
            this.isOpen = tag.getBoolean("is_open");
        } else {
            this.connectedNodePos = null;
            if (level != null && mainPos != null && level.isClientSide) {
                BlockEntity mainBE = level.getBlockEntity(mainPos);
                if (mainBE instanceof AbilityDeveloperBlockEntity mainDevBE) {
                    this.isOpen = mainDevBE.isOpen;
                    this.energyStored = mainDevBE.getEnergyStorage_internal();
                    this.connectedNodePos = mainDevBE.connectedNodePos;
                }
            }
        }
    }

    public void clientTick() {
        this.ticks++;
        if (getEnergyStorage() <= 0) {
            standState.stop();
            liedownState.startIfStopped(ticks);
        } else {
            liedownState.stop();
            standState.startIfStopped(ticks);
        }
        if (level != null && level.isClientSide) {
            this.openState.animateWhen(this.isOpen, this.ticks);
            this.closingState.animateWhen(!this.isOpen, this.ticks);
        }
    }

    public void serverTick() {
        this.ticks++;
        if (isMain() && connectedNodePos != null && level != null && level.getGameTime() % 100 == 0) {
            BlockEntity nodeBE = level.getBlockEntity(connectedNodePos);
            if (!(nodeBE instanceof org.academy.api.common.wireless.WirelessNode)) {
                setConnectedNodePosition(null);
            }
        }
    }
}