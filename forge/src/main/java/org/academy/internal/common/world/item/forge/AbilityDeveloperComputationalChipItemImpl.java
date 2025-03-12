package org.academy.internal.common.world.item.forge;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.academy.internal.common.world.level.block.entity.forge.AbilityDeveloperBlockEntity;
import org.jetbrains.annotations.NotNull;

public class AbilityDeveloperComputationalChipItemImpl {
    public static void check(@NotNull Level level, @NotNull Player player, @NotNull BlockHitResult blockHitResult) {
        if (level.getBlockEntity(blockHitResult.getBlockPos()) instanceof AbilityDeveloperBlockEntity abilityDeveloperBlockEntity) {
            if (abilityDeveloperBlockEntity.isEmpty()) {
                final ItemStack itemStack = player.getMainHandItem();
                final ItemStack newItemStack = itemStack.copy();
                abilityDeveloperBlockEntity.setItem(0, newItemStack);
                itemStack.shrink(1);
            }
        }
    }
}
