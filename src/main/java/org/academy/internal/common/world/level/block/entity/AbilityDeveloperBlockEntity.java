package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.jetbrains.annotations.NotNull;

public class AbilityDeveloperBlockEntity extends BlockEntity implements Container {
    public final NonNullList<ItemStack> items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
    public BlockPos mainPos = BlockPos.ZERO;

    public AbilityDeveloperBlockEntity(BlockPos pos, BlockState blockState) {
        super(AcademyCraftBlockEntityTypes.ABILITY_DEVELOPER, pos, blockState);
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

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public @NotNull ItemStack getItem(int slot) {
        return items.get(slot);
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
        items.set(slot, stack);
        if (stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
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
    protected void saveAdditional(CompoundTag tag) {
        if (!isMain()) {
            tag.putInt("mainPosX", mainPos.getX());
            tag.putInt("mainPosY", mainPos.getY());
            tag.putInt("mainPosZ", mainPos.getZ());
        }
        super.saveAdditional(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        if (!isMain()) {
            setMainPos(new BlockPos(tag.getInt("mainPosX"), tag.getInt("mainPosY"), tag.getInt("mainPosZ")));
        }
        super.load(tag);
    }
}