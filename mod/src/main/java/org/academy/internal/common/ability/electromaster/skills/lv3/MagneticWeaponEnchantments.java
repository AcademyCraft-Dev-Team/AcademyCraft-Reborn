package org.academy.internal.common.ability.electromaster.skills.lv3;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.academy.AcademyCraft;

public final class MagneticWeaponEnchantments {
    public static final ResourceKey<Enchantment> MAGNETIZED = ResourceKey.create(
            Registries.ENCHANTMENT, AcademyCraft.academy("magnetized"));

    private MagneticWeaponEnchantments() {
    }

    static boolean addTemporary(RegistryAccess access, ItemStack stack) {
        if (stack.isEmpty() || !EnchantmentHelper.canStoreEnchantments(stack)) return false;
        var holder = access.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(MAGNETIZED);
        if (stack.getEnchantmentLevel(holder) > 0) return false;
        EnchantmentHelper.updateEnchantments(stack, enchantments -> enchantments.set(holder, 1));
        return true;
    }

    static void removeTemporary(RegistryAccess access, ItemStack stack) {
        if (stack.isEmpty()) return;
        var holder = access.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(MAGNETIZED);
        EnchantmentHelper.updateEnchantments(stack, enchantments -> enchantments.set(holder, 0));
    }

    public static boolean isMagnetized(ItemStack stack) {
        return !stack.isEmpty() && EnchantmentHelper.getEnchantmentsForCrafting(stack)
                .keySet().stream().anyMatch(holder -> holder.is(MAGNETIZED));
    }
}
