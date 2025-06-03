package org.academy.internal.common.world.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import org.academy.internal.common.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class Items {
    public static final Map<String, Item> ITEMS = new HashMap<>();
    public static final Item ICON = register("icon", new Item(new Item.Properties()));
    public static final Item DATA_TERMINAL = register("data_terminal", new DataTerminalItem());
    public static final Item COIN = register("coin", new CoinItem());
    public static final Item WIRELESS_NODE = register("wireless_node", new BlockItem(Blocks.WIRELESS_NODE, new Item.Properties()));
    public static final Item WIND_GEN_BASE = register("wind_gen_base", new BlockItem(Blocks.WIND_GEN_BASE, new Item.Properties()));
    public static final Item WIND_GEN_TOP = register("wind_gen_top", new MultiBlockItem(Blocks.WIND_GEN_TOP, new Item.Properties()));
    public static final Item WIND_GEN_PILLAR = register("wind_gen_pillar", new BlockItem(Blocks.WIND_GEN_PILLAR, new Item.Properties()));
    public static final Item ABILITY_DEVELOPER = register("ability_developer", new MultiBlockItem(Blocks.ABILITY_DEVELOPER, new Item.Properties()));
    public static final Item WIND_GEN_FAN_ITEM = register("wind_gen_fan", new Item(new Item.Properties().stacksTo(16)));
    public static final Item IMAG_PHASE_INGOT = register("imag_phase_ingot", new Item(new Item.Properties()));
    public static final Item EMPTY_UNIT = register("empty_unit", new EmptyUnitItem());
    public static final Item IMAG_PHASE_UNIT = register("imag_phase_unit", new ImagPhaseUnitItem());
    public static final Item WIND_GEN_BASE_SCREEN = register("wind_gen_base_screen", new Item(new Item.Properties()));
    public static final Item IMAG_PHASE_CIRCUIT = register("imag_phase_circuit", new Item(new Item.Properties()));
    public static final Item IMAG_PHASE_POLYMER = register("imag_phase_polymer", new Item(new Item.Properties()));
    public static final Item IMAG_PHASE_PLATE = register("imag_phase_plate", new Item(new Item.Properties()));
    public static final Item IMAG_PHASE_DOWSING_ROD = register("imag_phase_dosing_rod", new ImagPhaseDosingRodItem());
    public static final Item IMAG_PHASE_VEGETATION = register("imag_phase_vegetation", new BlockItem(Blocks.IMAG_PHASE_VEGETATION, new Item.Properties()));
    public static final Item IMAG_PHASE_LEAVES = register("imag_phase_leaves", new BlockItem(Blocks.IMAG_PHASE_LEAVES, new Item.Properties()));
    public static final Item IMAG_PHASE_LOG = register("imag_phase_log", new BlockItem(Blocks.IMAG_PHASE_LOG, new Item.Properties()));
    public static final Item IMAG_PHASE_LICHEN = register("imag_phase_lichen", new BlockItem(Blocks.IMAG_PHASE_LICHEN, new Item.Properties()));

    public static <T extends Item> T register(String name, T item) {
        ITEMS.put(name, item);
        return item;
    }

    private Items() {
    }
}