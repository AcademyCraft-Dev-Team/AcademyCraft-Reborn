package org.academy.internal.common.world.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.academy.AcademyCraft;

import java.util.HashMap;
import java.util.Map;

public class AcademyCraftItems {
    public static final Map<ResourceLocation, Item> ITEMS = new HashMap<>();
    public static final Item ACADEMY_CRAFT_ICON_ITEM = new AcademyCraftIconItem();
    public static final Item DEVELOPER_PORTABLE_ITEM = new PortableDeveloperItem();
    public static final Item DATA_TERMINAL_ITEM = new DataTerminalItem();
    public static final Item COIN_ITEM = new CoinItem();
    public static final Item ADVANCED_WIRELESS_NODE_BLOCK_ITEM = new AdvancedWirelessNodeBlockItem();

    static {
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "icon"), ACADEMY_CRAFT_ICON_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "portable_developer"), DEVELOPER_PORTABLE_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "data_terminal"), DATA_TERMINAL_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "coin"), COIN_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "advanced_wireless_node_block"), ADVANCED_WIRELESS_NODE_BLOCK_ITEM);
    }

    private AcademyCraftItems() {
    }
}