package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilitySystem;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.NetworkResourceLocations;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.network.NetworkSystemServer;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public abstract class AbilityDeveloperBlockEntity extends BlockEntity implements Container {
    private final NonNullList<ItemStack> items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
    public BlockPos mainPos;
    public long energyStored;

    public abstract long getMaxStored();

    @SuppressWarnings("resource")
    public static void intiServer() {
        NetworkSystemServer.registerC2SPacketHandler(
                NetworkResourceLocations.C2S_LEARN_SKILL_PACKET,
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
                            abilityDeveloperBlockEntity.energyStored -= skill.level * 10000L;
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

    public AbilityDeveloperBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public void setMainPos(BlockPos pos) {
        this.mainPos = pos;
        setChanged();
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    public boolean isEmpty() {
        if (isMain()) {
            return items.stream().allMatch(ItemStack::isEmpty);
        } else {
            if (mainPos == null) return true;
            AcademyCraft.LOGGER.warn("isEmpty: mainPos is null");
            AbilityDeveloperBlockEntity abilityDeveloperBlockEntity =
                    (AbilityDeveloperBlockEntity) level.getBlockEntity(mainPos);
            if (abilityDeveloperBlockEntity == null) return true;
            AcademyCraft.LOGGER.warn("isEmpty: abilityDeveloperBlockEntity is null");
            return abilityDeveloperBlockEntity.isEmpty();
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    public @NotNull ItemStack getItem(int slot) {
        if (isMain()) {
            return items.get(slot);
        } else {
            if (mainPos == null) {
                AcademyCraft.LOGGER.warn("getItem: mainPos is null");
                return ItemStack.EMPTY;
            }
            BlockEntity blockEntity = level.getBlockEntity(mainPos);
            if (blockEntity instanceof AbilityDeveloperBlockEntity abilityDeveloperBlockEntity) {
                return abilityDeveloperBlockEntity.getItem(slot);
            } else {
                AcademyCraft.LOGGER.warn("getItem: blockEntity is null/wrong{}", blockEntity);
                return ItemStack.EMPTY;
            }
        }
    }

    @Override
    public @NotNull ItemStack removeItem(int slot, int amount) {
        ItemStack itemstack = ContainerHelper.removeItem(items, slot, amount);
        if (!itemstack.isEmpty()) {
            this.setChanged();
        }
        return itemstack;
    }

    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        if (isMain()) {
            items.set(slot, stack);
            if (stack.getCount() > this.getMaxStackSize()) {
                stack.setCount(this.getMaxStackSize());
            }
        } else {
            if (level != null &&
                    level.getBlockEntity(mainPos) instanceof AbilityDeveloperBlockEntity abilityDeveloperBlockEntity) {
                abilityDeveloperBlockEntity.setItem(slot, stack);
            }
        }
        this.setChanged();
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
    }

    public boolean isMain() {
        return this.getBlockState().getValue(AbilityDeveloperBlock.TYPE)
                .equals(AbilityDeveloperBlock.MultiBlockType.MAIN);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (mainPos != null) {
            tag.putInt("mainPosX", mainPos.getX());
            tag.putInt("mainPosY", mainPos.getY());
            tag.putInt("mainPosZ", mainPos.getZ());
            if (isMain()) {
                ContainerHelper.saveAllItems(tag, items);
                tag.putLong("energyStored", energyStored);
            }
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (isMain()) {
            ContainerHelper.loadAllItems(tag, items);
            energyStored = tag.getLong("energyStored");
        }
        mainPos = new BlockPos(
                tag.getInt("mainPosX"),
                tag.getInt("mainPosY"),
                tag.getInt("mainPosZ")
        );
    }
}