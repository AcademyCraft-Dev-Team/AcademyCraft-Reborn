package org.academy.fabric.internal.common.world.level.block.entity.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.fabric.internal.common.world.level.block.fabric.AbilityDeveloperBlock;
import org.jetbrains.annotations.NotNull;

public class AbilityDeveloperBlockEntity extends BlockEntity implements Container {
    public final NonNullList<ItemStack> items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
    public BlockPos mainPos = BlockPos.ZERO;

    public AbilityDeveloperBlockEntity(BlockPos pos, BlockState blockState) {
        super(AcademyCraftBlockEntityTypesFabric.ABILITY_DEVELOPER, pos, blockState);
        if (isMain()) {
            setMainPos(pos);
        }
    }

    public void setMainPos(BlockPos pos) {
        this.mainPos = pos;
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
            AbilityDeveloperBlockEntity abilityDeveloperBlockEntity = (AbilityDeveloperBlockEntity) level.getBlockEntity(mainPos);
            return abilityDeveloperBlockEntity.isEmpty();
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    public @NotNull ItemStack getItem(int slot) {
        if (isMain()) {
            return items.get(slot);
        } else {
            AbilityDeveloperBlockEntity abilityDeveloperBlockEntity = (AbilityDeveloperBlockEntity) level.getBlockEntity(mainPos);
            return abilityDeveloperBlockEntity.getItem(slot);
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
            if (level != null && level.getBlockEntity(mainPos) instanceof AbilityDeveloperBlockEntity abilityDeveloperBlockEntity) {
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
        return this.getBlockState().getValue(AbilityDeveloperBlock.TYPE).equals(AbilityDeveloperBlock.MultiBlockType.MAIN);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        if (!isMain()) {
            tag.putInt("mainPosX", mainPos.getX());
            tag.putInt("mainPosY", mainPos.getY());
            tag.putInt("mainPosZ", mainPos.getZ());
        } else {
            ContainerHelper.saveAllItems(tag, items);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        if (!isMain()) {
            setMainPos(new BlockPos(tag.getInt("mainPosX"), tag.getInt("mainPosY"), tag.getInt("mainPosZ")));
        } else {
            ContainerHelper.loadAllItems(tag, items);
        }
    }
}