package org.academy.internal.common.world.inventory;

import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.HashMap;
import java.util.Map;

public class MenuTypes {
    public static final Map<String, MenuType<?>> MENU_TYPES = new HashMap<>();

    public static final MenuType<WindGenMenu> WIND_GEN_MENU = register("wind_gen", WindGenMenu::new, FeatureFlags.VANILLA_SET);
    public static final MenuType<NodeMenu> NODE_MENU = register("node", NodeMenu::new, FeatureFlags.VANILLA_SET);

    public static <T extends AbstractContainerMenu> MenuType<T> register(String name, MenuType.MenuSupplier<T> constructor, FeatureFlagSet requiredFeatures) {
        MenuType<T> menuType = new MenuType<>(constructor, requiredFeatures);
        MENU_TYPES.put(name, menuType);
        return menuType;
    }

    private MenuTypes() {
    }
}