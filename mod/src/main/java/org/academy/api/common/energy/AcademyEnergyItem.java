package org.academy.api.common.energy;

import net.minecraft.world.item.ItemStack;

/**
 * Native AcademyCraft energy storage exposed by an item stack.
 *
 * <p>Implementations own the stored-energy value. Platform energy capabilities should adapt this
 * same value instead of maintaining a second energy store.</p>
 */
public interface AcademyEnergyItem {
    int getEnergyStored(ItemStack stack);

    int getMaxEnergyStored(ItemStack stack);

    int receiveEnergy(ItemStack stack, int maxReceive, boolean simulate);

    int extractEnergy(ItemStack stack, int maxExtract, boolean simulate);
}
