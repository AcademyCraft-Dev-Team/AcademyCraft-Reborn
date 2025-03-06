package org.academy.internal.common.world.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.academy.AcademyCraft;

import java.util.HashMap;
import java.util.Map;

public class AcademyCraftItems {
    public static final Map<ResourceLocation, Item> ITEMS = new HashMap<>();
    public static final AcademyCraftIconItem ACADEMY_CRAFT_ICON_ITEM = new AcademyCraftIconItem();
    public static final PortableDeveloperItem DEVELOPER_PORTABLE_ITEM = new PortableDeveloperItem();
    public static final DataTerminalItem DATA_TERMINAL_ITEM = new DataTerminalItem();
    public static final CoinItem COIN_ITEM = new CoinItem();
    public static final AbilityDeveloperBlockItem ABILITY_DEVELOPER_BLOCK_ITEM = new AbilityDeveloperBlockItem();

    static {
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "icon"), ACADEMY_CRAFT_ICON_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "portable_developer"), DEVELOPER_PORTABLE_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "data_terminal"), DATA_TERMINAL_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "coin"), COIN_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "ability_developer_block_item"), ABILITY_DEVELOPER_BLOCK_ITEM);
    }

    private AcademyCraftItems() {
    }
}
