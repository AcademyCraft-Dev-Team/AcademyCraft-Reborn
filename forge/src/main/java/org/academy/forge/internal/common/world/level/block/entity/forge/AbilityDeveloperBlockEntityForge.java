package org.academy.forge.internal.common.world.level.block.entity.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.forge.internal.common.world.level.block.forge.AbilityDeveloperBlockForge;
import org.jetbrains.annotations.NotNull;

public class AbilityDeveloperBlockEntityForge extends BlockEntity implements Container {
    public final NonNullList<ItemStack> items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
    public BlockPos mainPos;

    public AbilityDeveloperBlockEntityForge(BlockPos pos, BlockState blockState) {
        super(AcademyCraftBlockEntityTypesForge.ABILITY_DEVELOPER, pos, blockState);
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
            AbilityDeveloperBlockEntityForge abilityDeveloperBlockEntityForge = (AbilityDeveloperBlockEntityForge) level.getBlockEntity(mainPos);
            return abilityDeveloperBlockEntityForge.isEmpty();
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    public @NotNull ItemStack getItem(int slot) {
        if (isMain()) {
            return items.get(slot);
        } else {
            AbilityDeveloperBlockEntityForge abilityDeveloperBlockEntityForge = (AbilityDeveloperBlockEntityForge) level.getBlockEntity(mainPos);
            return abilityDeveloperBlockEntityForge.getItem(slot);
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
            if (level != null && level.getBlockEntity(mainPos) instanceof AbilityDeveloperBlockEntityForge abilityDeveloperBlockEntityForge) {
                abilityDeveloperBlockEntityForge.setItem(slot, stack);
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
        return this.getBlockState().getValue(AbilityDeveloperBlockForge.TYPE).equals(AbilityDeveloperBlockForge.MultiBlockType.MAIN);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        tag.putInt("mainPosX", mainPos.getX());
        tag.putInt("mainPosY", mainPos.getY());
        tag.putInt("mainPosZ", mainPos.getZ());
        if (isMain()) {
            ContainerHelper.saveAllItems(tag, items);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        setMainPos(new BlockPos(
                        tag.getInt("mainPosX"),
                        tag.getInt("mainPosY"),
                        tag.getInt("mainPosZ")
                )
        );
        if (isMain()) {
            ContainerHelper.loadAllItems(tag, items);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        Vec3 pos = this.getBlockPos().getCenter();
        double radius = 5.0;
        return new AABB(pos.x - radius, pos.y - radius, pos.z - radius, pos.x + radius, pos.y + radius, pos.z + radius);
    }
}