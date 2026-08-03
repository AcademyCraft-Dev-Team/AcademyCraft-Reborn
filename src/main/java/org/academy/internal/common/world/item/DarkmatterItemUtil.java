package org.academy.internal.common.world.item;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.academy.internal.common.ability.darkmatter.DarkmatterEnchantments;

import java.util.Set;

public final class DarkmatterItemUtil {
    private static final Set<Item> VANILLA_DUPLICABLE_MATERIALS = Set.of(
            net.minecraft.world.item.Items.IRON_INGOT,
            net.minecraft.world.item.Items.GOLD_INGOT,
            net.minecraft.world.item.Items.COPPER_INGOT,
            net.minecraft.world.item.Items.NETHERITE_INGOT,
            net.minecraft.world.item.Items.IRON_NUGGET,
            net.minecraft.world.item.Items.GOLD_NUGGET,
            net.minecraft.world.item.Items.DIAMOND,
            net.minecraft.world.item.Items.EMERALD,
            net.minecraft.world.item.Items.QUARTZ,
            net.minecraft.world.item.Items.AMETHYST_SHARD,
            net.minecraft.world.item.Items.PRISMARINE_SHARD,
            net.minecraft.world.item.Items.PRISMARINE_CRYSTALS,
            net.minecraft.world.item.Items.LAPIS_LAZULI,
            net.minecraft.world.item.Items.REDSTONE,
            net.minecraft.world.item.Items.COAL,
            net.minecraft.world.item.Items.CHARCOAL,
            net.minecraft.world.item.Items.NETHERITE_SCRAP,
            net.minecraft.world.item.Items.RAW_IRON,
            net.minecraft.world.item.Items.RAW_GOLD,
            net.minecraft.world.item.Items.RAW_COPPER
    );

    private DarkmatterItemUtil() {
    }

    public static boolean isDarkmatter(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.DARKMATTER.get());
    }

    public static boolean isDuplicableMaterial(ItemStack stack) {
        if (stack.isEmpty() || isDarkmatter(stack) || stack.isDamageableItem()
                || stack.getMaxStackSize() <= 1) return false;
        if (VANILLA_DUPLICABLE_MATERIALS.contains(stack.getItem())) return true;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        var path = id.getPath();
        return path.contains("ingot") || path.contains("nugget") || path.contains("gem")
                || path.contains("dust") || path.contains("crystal") || path.contains("shard")
                || path.contains("raw_") || path.contains("plate") || path.contains("rod");
    }

    public static ItemStack createDuplicatedMaterialResult(ItemStack material) {
        if (!isDuplicableMaterial(material)) return ItemStack.EMPTY;
        var result = material.copy();
        result.setCount(Math.min(result.getMaxStackSize(), 2));
        return result;
    }

    public static boolean repairDurability(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem() || !stack.isDamaged()) return false;
        stack.setDamageValue(0);
        return true;
    }

    public static boolean toggleEnchantment(RegistryAccess access, ItemStack stack,
                                            ResourceKey<Enchantment> key) {
        if (stack.isEmpty() || !EnchantmentHelper.canStoreEnchantments(stack)) return false;
        var registry = access.lookupOrThrow(Registries.ENCHANTMENT);
        var holder = registry.getOrThrow(key);
        var enabled = stack.getEnchantmentLevel(holder) > 0;
        EnchantmentHelper.updateEnchantments(stack,
                enchantments -> enchantments.set(holder, enabled ? 0 : 1));
        return true;
    }

    public static boolean addEnchantment(RegistryAccess access, ItemStack stack,
                                         ResourceKey<Enchantment> key) {
        if (stack.isEmpty() || !EnchantmentHelper.canStoreEnchantments(stack)) return false;
        var registry = access.lookupOrThrow(Registries.ENCHANTMENT);
        var holder = registry.getOrThrow(key);
        if (stack.getEnchantmentLevel(holder) > 0) return false;
        EnchantmentHelper.updateEnchantments(stack, enchantments -> enchantments.set(holder, 1));
        return true;
    }

    public static ItemStack createAnvilUpgradeResult(RegistryAccess access, ItemStack left,
                                                     String renameText) {
        if (left.isEmpty()) return ItemStack.EMPTY;
        var result = left.copy();
        var changed = repairDurability(result)
                | addEnchantment(access, result, DarkmatterEnchantments.DARKMATTER);
        if (renameText != null) {
            var trimmed = renameText.trim();
            if (trimmed.isEmpty() && left.has(DataComponents.CUSTOM_NAME)) {
                result.remove(DataComponents.CUSTOM_NAME);
                changed = true;
            } else if (!trimmed.isEmpty() && !trimmed.equals(left.getHoverName().getString())) {
                result.set(DataComponents.CUSTOM_NAME, Component.literal(trimmed));
                changed = true;
            }
        }
        return changed ? result : ItemStack.EMPTY;
    }

    public static boolean hasEnchantment(ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack.isEmpty()) return false;
        return EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet().stream()
                .anyMatch(holder -> holder.is(key));
    }

    public static boolean hasFamilyEnchantment(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet().stream().anyMatch(holder ->
                holder.is(DarkmatterEnchantments.DARKMATTER)
                        || holder.is(DarkmatterEnchantments.DARKMATTER_DEFENSE));
    }
}
