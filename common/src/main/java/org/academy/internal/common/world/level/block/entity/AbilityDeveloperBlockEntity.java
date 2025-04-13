package org.academy.internal.common.world.level.block.entity;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilitySystem;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.Packets;
import org.academy.api.common.wireless.WirelessNode;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.network.NetworkSystemServer;
import org.academy.internal.client.models.AbilityDeveloperBlockEntityModel;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public abstract class AbilityDeveloperBlockEntity extends BlockEntity implements WirelessUser {
    @Nullable
    public WirelessNode wirelessNode;
    public String name;
    public BlockPos mainPos;
    public int energyStored;
    public int ticks;
    public final AnimationState animationState = new AnimationState();
    public final AnimationState closingAnimationState = new AnimationState();
    public boolean isOpen = false;

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
                            abilityDeveloperBlockEntity.setChanged();
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
        this.animationState.animateWhen(this.isOpen, this.ticks);
        this.closingAnimationState.animateWhen(!this.isOpen, this.ticks);

        boolean previousIsOpen = this.isOpen;
        this.isOpen = open;

        if (level != null && level.isClientSide) {
            AnimationState currentAnimationState = previousIsOpen ? animationState : closingAnimationState;
            AnimationState targetAnimationState = open ? animationState : closingAnimationState;
            AnimationDefinition targetAnimationDefinition = open ? AbilityDeveloperBlockEntityModel.open : AbilityDeveloperBlockEntityModel.close;

            long elapsedMillis = currentAnimationState.getAccumulatedTime();

            currentAnimationState.stop();

            if (elapsedMillis > 0) {
                float elapsedSeconds = elapsedMillis / 1000.0f;
                float totalDuration = targetAnimationDefinition.lengthInSeconds();

                float targetStartSeconds = Math.max(0.0f, Math.min(totalDuration, totalDuration - elapsedSeconds));

                long targetElapsedTicks = (long)(targetStartSeconds * 20.0f);

                long adjustedStartTick = this.ticks - targetElapsedTicks;
                targetAnimationState.start(Math.toIntExact(adjustedStartTick));
            } else {
                targetAnimationState.start(this.ticks);
            }
        }

        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public boolean isMain() {
        return this.getBlockState().getValue(AbilityDeveloperBlock.TYPE)
                .equals(AbilityDeveloperBlock.MultiBlockType.MAIN);
    }

    @Override
    public @Nullable WirelessNode getWirelessNode() {
        return wirelessNode;
    }

    @Override
    public void setWirelessNode(@Nullable WirelessNode wirelessNode) {
        this.wirelessNode = wirelessNode;
    }

    @Override
    public String getName() {
        return "AbilityDeveloperBlockEntity: " + mainPos;
    }

    @Override
    public int getEnergyStorage() {
        if (!isMain() && level != null && mainPos != null) {
            BlockEntity mainBE = level.getBlockEntity(mainPos);
            if (mainBE instanceof AbilityDeveloperBlockEntity mainDevBE) {
                return mainDevBE.getEnergyStorage();
            }
        }
        return energyStored;
    }

    @Override
    public void setEnergyStorage(int energyStorage) {
        if (!isMain() && level != null && mainPos != null) {
            BlockEntity mainBE = level.getBlockEntity(mainPos);
            if (mainBE instanceof AbilityDeveloperBlockEntity mainDevBE) {
                mainDevBE.setEnergyStorage(energyStorage);
                return;
            }
        }
        this.energyStored = energyStorage;
        setChanged();
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
            tag.putBoolean("isOpen", isOpen);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains("mainPosX")) {
            mainPos = new BlockPos(
                    tag.getInt("mainPosX"),
                    tag.getInt("mainPosY"),
                    tag.getInt("mainPosZ")
            );
        }

        if (isMain()) {
            energyStored = tag.getInt("energyStored");
            boolean loadedIsOpen = tag.getBoolean("isOpen");
            if (level == null || !level.isClientSide) {
                this.isOpen = loadedIsOpen;
            } else {
                if (this.isOpen != loadedIsOpen) {
                    this.isOpen = loadedIsOpen;
                }
            }

        } else if (level != null && mainPos != null && level.isClientSide) {
            BlockEntity mainBE = level.getBlockEntity(mainPos);
            if (mainBE instanceof AbilityDeveloperBlockEntity mainDevBE) {
                this.isOpen = mainDevBE.isOpen;
                this.energyStored = mainDevBE.energyStored;
            }
        }
    }

    public void clientTick() {
        this.ticks++;
    }

    public void serverTick() {
        this.ticks++;
    }
}