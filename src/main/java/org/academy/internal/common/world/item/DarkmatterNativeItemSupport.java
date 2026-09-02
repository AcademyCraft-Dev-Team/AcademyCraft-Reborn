package org.academy.internal.common.world.item;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.academy.api.common.ability.darkmatter.DarkmatterIntegrity;
import org.academy.api.common.ability.darkmatter.DarkmatterShapingProfile;

/**
 * Shared construction and durability behavior for vanilla-derived shaped item classes.
 */
public final class DarkmatterNativeItemSupport {
    public static final int ENCHANTABILITY = 25;

    private DarkmatterNativeItemSupport() {
    }

    public static Item.Properties enchantableProperties(Item.Properties properties) {
        return properties.enchantable(ENCHANTABILITY);
    }

    public static Item.Properties equipmentProperties(Item.Properties properties) {
        return enchantableProperties(properties).durability(Integer.MAX_VALUE).setNoCombineRepair()
                .component(ItemDataComponents.DARKMATTER_INTEGRITY.get(), DarkmatterIntegrity.FULL)
                .component(ItemDataComponents.DARKMATTER_SHAPING_PROFILE.get(),
                        DarkmatterShapingProfile.DEFAULT);
    }

    public static Item.Properties ammunitionProperties(Item.Properties properties) {
        return enchantableProperties(properties).stacksTo(64).component(
                ItemDataComponents.DARKMATTER_SHAPING_PROFILE.get(),
                DarkmatterShapingProfile.DEFAULT);
    }

    public static boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment,
                                              boolean vanillaResult) {
        return !enchantment.is(Enchantments.MENDING) && vanillaResult;
    }

    public static boolean isBarVisible(ItemStack stack) {
        return DarkmatterItemUtil.integrity(stack) < 0.99999f;
    }

    public static int barWidth(ItemStack stack) {
        return Math.round(13.0f * DarkmatterItemUtil.integrity(stack));
    }

    public static int barColor(ItemStack stack) {
        return Mth.hsvToRgb(DarkmatterItemUtil.integrity(stack) / 3.0f, 1.0f, 1.0f);
    }

    public static boolean isSupportedArrow(ItemStack stack) {
        return stack.getItem() instanceof ArrowItem
                || stack.is(net.minecraft.world.item.Items.ARROW);
    }

    /**
     * Creates the non-pickup fallback projectile used when a native bow has no ammo.
     */
    public static ItemStack infiniteDarkmatterArrow(ItemStack weapon) {
        var projectile = new ItemStack(Items.DARKMATTER_ARROW.get());
        if (DarkmatterItemUtil.isShapedItem(weapon)) {
            DarkmatterItemUtil.setShapingProfile(
                    projectile, DarkmatterItemUtil.shapingProfile(weapon));
        }
        projectile.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
        return projectile;
    }

    /**
     * Integrity is synchronized continuously while an item is carried. It is visual state,
     * not an item swap, so it must not repeatedly lower the first-person item out of view.
     */
    public static boolean shouldCauseReequipAnimation(
            ItemStack oldStack, ItemStack newStack, boolean slotChanged
    ) {
        return slotChanged || gameplayComponentsChanged(oldStack, newStack);
    }

    /**
     * Integrity/damage drift must not reset an in-progress block break.
     */
    public static boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return gameplayComponentsChanged(oldStack, newStack);
    }

    private static boolean gameplayComponentsChanged(ItemStack oldStack, ItemStack newStack) {
        if (!newStack.is(oldStack.getItem())) return true;
        var oldComparable = oldStack.copy();
        var newComparable = newStack.copy();
        oldComparable.remove(DataComponents.DAMAGE);
        newComparable.remove(DataComponents.DAMAGE);
        oldComparable.remove(ItemDataComponents.DARKMATTER_INTEGRITY.get());
        newComparable.remove(ItemDataComponents.DARKMATTER_INTEGRITY.get());
        return !ItemStack.isSameItemSameComponents(oldComparable, newComparable);
    }
}
