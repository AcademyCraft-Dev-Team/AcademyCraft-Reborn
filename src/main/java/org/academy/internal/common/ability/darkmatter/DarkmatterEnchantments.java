package org.academy.internal.common.ability.darkmatter;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import org.academy.AcademyCraft;

public final class DarkmatterEnchantments {
    public static final ResourceKey<Enchantment> DARKMATTER = ResourceKey.create(
            Registries.ENCHANTMENT, AcademyCraft.academy("darkmatter"));
    public static final ResourceKey<Enchantment> DARKMATTER_DEFENSE = ResourceKey.create(
            Registries.ENCHANTMENT, AcademyCraft.academy("darkmatter_defense"));

    private DarkmatterEnchantments() {
    }
}
