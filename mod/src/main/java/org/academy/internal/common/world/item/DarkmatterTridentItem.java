package org.academy.internal.common.world.item;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import org.academy.api.common.ability.darkmatter.DarkmatterShape;

public final class DarkmatterTridentItem extends TridentItem implements DarkmatterShapedItem {
    public DarkmatterTridentItem(Properties properties) {
        super(DarkmatterNativeItemSupport.equipmentProperties(properties));
    }

    @Override
    public DarkmatterShape darkmatterShape() {
        return DarkmatterShape.TRIDENT;
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
            ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return DarkmatterNativeItemSupport.shouldCauseReequipAnimation(
                oldStack, newStack, slotChanged);
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return DarkmatterNativeItemSupport.shouldCauseBlockBreakReset(oldStack, newStack);
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return DarkmatterNativeItemSupport.supportsEnchantment(
                stack, enchantment, super.supportsEnchantment(stack, enchantment));
    }
}
