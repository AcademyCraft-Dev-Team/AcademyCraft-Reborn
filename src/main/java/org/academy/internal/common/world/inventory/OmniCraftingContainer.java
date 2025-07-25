package org.academy.internal.common.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface OmniCraftingContainer extends Container, StackedContentsCompatible {
    int getWidth();

    int getHeight();

    List<ItemStack> getItems();
}