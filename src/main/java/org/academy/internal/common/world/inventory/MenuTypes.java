package org.academy.internal.common.world.inventory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static org.academy.AcademyCraft.MODID;

public class MenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(BuiltInRegistries.MENU, MODID);
    public static final DeferredHolder<MenuType<?>, MenuType<WindGenMenu>> WIND_GEN = MENU_TYPES.register("wind_gen",
            () -> new MenuType<>(WindGenMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<WirelessNodeMenu>> NODE = MENU_TYPES.register("node",
            () -> new MenuType<>(WirelessNodeMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<OmniCraftingMenu>> OMNI_CRAFTING_TABLE = MENU_TYPES.register("omni_crafting",
            () -> new MenuType<>(OmniCraftingMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<SolarGenMenu>> SOLAR_GEN = MENU_TYPES.register("solar_gen",
            () -> new MenuType<>(SolarGenMenu::new, FeatureFlags.VANILLA_SET));

    private MenuTypes() {
    }
}