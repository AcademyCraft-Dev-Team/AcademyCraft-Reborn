package org.academy.internal.common.world.item;

import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.academy.api.common.ability.darkmatter.DarkmatterShape;

/**
 * Base item for the six native pieces whose structural integrity never breaks the stack.
 */
public class DarkmatterEquipmentItem extends Item implements DarkmatterShapedItem {
    public DarkmatterEquipmentItem(Properties properties) {
        // Structural integrity is authoritative. A practically unreachable vanilla durability
        // ceiling prevents ItemStack's ordinary break path from deleting the item between ticks.
        super(DarkmatterNativeItemSupport.equipmentProperties(properties));
    }

    @Override
    public DarkmatterShape darkmatterShape() {
        return DarkmatterShape.ARMOR;
    }

    @Override
    public boolean isCombineRepairable(ItemStack stack) {
        return false;
    }

    @Override
    public float getXpRepairRatio(ItemStack stack) {
        return 0.0f;
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return !enchantment.is(Enchantments.MENDING)
                && super.supportsEnchantment(stack, enchantment);
    }

    @Override
    public boolean canGrindstoneRepair(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return DarkmatterNativeItemSupport.isBarVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return DarkmatterNativeItemSupport.barWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return DarkmatterNativeItemSupport.barColor(stack);
    }

    @Override
    public boolean shouldCauseReequipAnimation(
            ItemStack oldStack, ItemStack newStack, boolean slotChanged
    ) {
        return DarkmatterNativeItemSupport.shouldCauseReequipAnimation(
                oldStack, newStack, slotChanged);
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return DarkmatterNativeItemSupport.shouldCauseBlockBreakReset(oldStack, newStack);
    }
}
