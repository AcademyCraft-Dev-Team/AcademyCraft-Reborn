package org.academy.internal.common.world.item;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.academy.api.common.ability.darkmatter.DarkmatterIntegrity;
import org.academy.api.common.ability.darkmatter.DarkmatterShape;
import org.academy.api.common.ability.darkmatter.DarkmatterShapingProfile;
import org.academy.internal.common.ability.darkmatter.DarkmatterEnchantments;
import org.academy.internal.common.ability.darkmatter.DarkmatterIntegrityCurve;

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

    public static boolean isNativeEquipment(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof DarkmatterShapedItem shaped
                && shaped.usesDarkmatterIntegrity();
    }

    public static boolean isShapedItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof DarkmatterShapedItem;
    }

    public static DarkmatterShape shape(ItemStack stack) {
        if (stack.getItem() instanceof DarkmatterShapedItem shaped) {
            return shaped.darkmatterShape();
        }
        var item = stack.getItem();
        if (item instanceof ArrowItem) return DarkmatterShape.ARROW;
        if (item instanceof CrossbowItem) return DarkmatterShape.CROSSBOW;
        if (item instanceof BowItem || item instanceof ProjectileWeaponItem) return DarkmatterShape.BOW;
        if (item instanceof TridentItem) return DarkmatterShape.TRIDENT;
        if (item instanceof MaceItem) return DarkmatterShape.MACE;
        return DarkmatterShape.TOOL;
    }

    public static DarkmatterShapingProfile shapingProfile(ItemStack stack) {
        if (!isShapedItem(stack)) return DarkmatterShapingProfile.DEFAULT;
        return stack.getOrDefault(ItemDataComponents.DARKMATTER_SHAPING_PROFILE.get(),
                DarkmatterShapingProfile.DEFAULT);
    }

    public static void setShapingProfile(ItemStack stack, DarkmatterShapingProfile profile) {
        if (isShapedItem(stack)) {
            stack.set(ItemDataComponents.DARKMATTER_SHAPING_PROFILE.get(), profile);
        }
    }

    public static int modifierLevel(ItemStack stack, String id) {
        var level = hasNativeItemEffects(stack) ? shapingProfile(stack).modifierLevel(id) : 0;
        var coating = stack.get(ItemDataComponents.DARKMATTER_COATING_PROFILE.get());
        return level + (coating == null ? 0 : coating.modifierLevel(id));
    }

    public static boolean hasCoating(ItemStack stack) {
        return !stack.isEmpty() && stack.has(ItemDataComponents.DARKMATTER_COATING_PROFILE.get());
    }

    public static DarkmatterShapingProfile coatingProfile(ItemStack stack) {
        return stack.getOrDefault(ItemDataComponents.DARKMATTER_COATING_PROFILE.get(),
                DarkmatterShapingProfile.DEFAULT);
    }

    public static boolean hasNativeItemEffects(ItemStack stack) {
        return isShapedItem(stack) && shape(stack).carriesActiveItemEffects();
    }

    public static boolean hasShapingEffects(ItemStack stack) {
        return !stack.isEmpty() && (hasNativeItemEffects(stack) || hasCoating(stack));
    }

    public static float effectAlphaPower(ItemStack stack) {
        var result = hasNativeItemEffects(stack) ? shapingProfile(stack).alphaPower() : 0.0f;
        if (hasCoating(stack)) result += coatingProfile(stack).alphaPower();
        return result;
    }

    public static float effectBetaPower(ItemStack stack) {
        var result = hasNativeItemEffects(stack) ? shapingProfile(stack).betaPower() : 0.0f;
        if (hasCoating(stack)) result += coatingProfile(stack).betaPower();
        return result;
    }

    public static float integrity(ItemStack stack) {
        if (!isNativeEquipment(stack)) return 1.0f;
        return stack.getOrDefault(
                ItemDataComponents.DARKMATTER_INTEGRITY.get(), DarkmatterIntegrity.FULL).value();
    }

    public static boolean isOperational(ItemStack stack) {
        return !isNativeEquipment(stack) || integrity(stack) > 1.0e-5f;
    }

    public static boolean setIntegrity(ItemStack stack, float value) {
        if (!isNativeEquipment(stack)) return false;
        return applyIntegrity(stack, new DarkmatterIntegrity(value));
    }

    private static boolean applyIntegrity(ItemStack stack, DarkmatterIntegrity next) {
        var previous = stack.getOrDefault(
                ItemDataComponents.DARKMATTER_INTEGRITY.get(), DarkmatterIntegrity.FULL);
        if (previous.equals(next)) return false;
        stack.set(ItemDataComponents.DARKMATTER_INTEGRITY.get(), next);
        updateOperationalAttributes(stack, next.operational());
        return true;
    }

    public static boolean damageIntegrity(ItemStack stack, float amount) {
        if (!Float.isFinite(amount) || amount <= 0.0f || !isNativeEquipment(stack)) return false;
        return setIntegrity(stack, integrity(stack) - amount);
    }

    /**
     * Passive carry decay uses an end-step snap so floating-point drift cannot extend the lifetime.
     */
    public static boolean decayIntegrity(ItemStack stack, int lifetimeTicks) {
        if (!isNativeEquipment(stack) || lifetimeTicks <= 0) return false;
        var previous = stack.getOrDefault(
                ItemDataComponents.DARKMATTER_INTEGRITY.get(), DarkmatterIntegrity.FULL);
        var step = DarkmatterIntegrityCurve.nextPassiveIntegrity(
                previous.value(), previous.decayRemainder(), lifetimeTicks);
        return applyIntegrity(stack, new DarkmatterIntegrity(step.value(), step.remainder()));
    }

    public static boolean repairIntegrity(ItemStack stack, float amount) {
        if (!Float.isFinite(amount) || amount <= 0.0f || !isNativeEquipment(stack)) return false;
        return setIntegrity(stack, integrity(stack) + amount);
    }

    /**
     * Converts ordinary durability damage into structural loss before resetting vanilla damage.
     */
    public static boolean absorbVanillaDurabilityDamage(ItemStack stack) {
        if (!isNativeEquipment(stack) || !stack.isDamageableItem()
                || stack.getDamageValue() <= 0 || stack.getMaxDamage() <= 0) return false;
        // One ordinary durability point equals one passive-decay tick. This keeps active wear in
        // the same integrity ledger without depending on the anti-deletion vanilla max damage.
        var loss = stack.getDamageValue() / 12_000.0f;
        stack.setDamageValue(0);
        damageIntegrity(stack, loss);
        return true;
    }

    private static void updateOperationalAttributes(ItemStack stack, boolean operational) {
        if (!operational) {
            stack.remove(DataComponents.ATTRIBUTE_MODIFIERS);
            stack.remove(DataComponents.TOOL);
            return;
        }
        var defaults = stack.getItem().getDefaultInstance();
        var attributes = defaults.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (attributes != null) stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes);
        var tool = defaults.get(DataComponents.TOOL);
        if (tool != null) stack.set(DataComponents.TOOL, tool);
    }

    public static boolean canStoreEnchantments(ItemStack stack) {
        return !stack.isEmpty() && EnchantmentHelper.canStoreEnchantments(stack);
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
        if (isNativeEquipment(stack)) return false;
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

    public static boolean setEnchantmentLevel(
            RegistryAccess access,
            ItemStack stack,
            ResourceKey<Enchantment> key,
            int level
    ) {
        if (stack.isEmpty() || !EnchantmentHelper.canStoreEnchantments(stack)) return false;
        var holder = access.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        var normalized = Math.max(0, level);
        if (stack.getEnchantmentLevel(holder) == normalized) return false;
        EnchantmentHelper.updateEnchantments(stack, enchantments -> enchantments.set(holder, normalized));
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
