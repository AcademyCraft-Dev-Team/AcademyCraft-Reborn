package org.academy.internal.common.world.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.academy.AcademyCraft;

import java.util.HashMap;
import java.util.Map;

public class Items {
    public static final Map<ResourceLocation, Item> ITEMS = new HashMap<>();
    public static final Item ACADEMY_CRAFT_ICON_ITEM = new AcademyCraftIconItem();
    public static final Item DEVELOPER_PORTABLE_ITEM = new PortableDeveloperItem();
    public static final Item DATA_TERMINAL_ITEM = new DataTerminalItem();
    public static final Item COIN_ITEM = new CoinItem();
    public static final Item ADVANCED_WIRELESS_NODE_BLOCK_ITEM = new AdvancedWirelessNodeBlockItem();
    public static final Item WIND_GEN_BASE_BLOCK_ITEM = new WindGenBaseBlockItem();
    public static final Item WIND_GEN_TOP_BLOCK_ITEM = new WindGenTopBlockItem();
    public static final Item WIND_GEN_PILLAR_BLOCK_ITEM = new WindGenPillarBlockItem();
    public static final Item ABILITY_DEVELOPER_BLOCK_ITEM = new AbilityDeveloperBlockItem();

    static {
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "icon"), ACADEMY_CRAFT_ICON_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "portable_developer"), DEVELOPER_PORTABLE_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "data_terminal"), DATA_TERMINAL_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "coin"), COIN_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "advanced_wireless_node_block"), ADVANCED_WIRELESS_NODE_BLOCK_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "wind_gen_base_block"), WIND_GEN_BASE_BLOCK_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "wind_gen_top_block"), WIND_GEN_TOP_BLOCK_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "wind_gen_pillar_block"), WIND_GEN_PILLAR_BLOCK_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "ability_developer_block"), ABILITY_DEVELOPER_BLOCK_ITEM);
    }

    private Items() {
    }
}