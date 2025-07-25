package org.academy.internal.common.world.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.internal.common.world.level.block.Blocks;

import static org.academy.AcademyCraft.MODID;

@SuppressWarnings("unused")
public final class Items {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, MODID);
    public static final DeferredHolder<Item, Item> ICON = ITEMS.register("icon", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, DataTerminalItem> DATA_TERMINAL = ITEMS.register("data_terminal", DataTerminalItem::new);
    public static final DeferredHolder<Item, CoinItem> COIN = ITEMS.register("coin", CoinItem::new);
    public static final DeferredHolder<Item, BlockItem> WIRELESS_NODE = ITEMS.register("wireless_node",
            () -> new BlockItem(Blocks.WIRELESS_NODE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> WIND_GEN_BASE = ITEMS.register("wind_gen_base",
            () -> new BlockItem(Blocks.WIND_GEN_BASE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, MultiBlockItem> WIND_GEN_TOP = ITEMS.register("wind_gen_top",
            () -> new MultiBlockItem(Blocks.WIND_GEN_TOP.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> WIND_GEN_PILLAR = ITEMS.register("wind_gen_pillar",
            () -> new BlockItem(Blocks.WIND_GEN_PILLAR.get(), new Item.Properties()));
    public static final DeferredHolder<Item, MultiBlockItem> ABILITY_DEVELOPER = ITEMS.register("ability_developer",
            () -> new MultiBlockItem(Blocks.ABILITY_DEVELOPER.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> WIND_GEN_FAN_ITEM = ITEMS.register("wind_gen_fan",
            () -> new Item(new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<Item, Item> IMAGIPHASE_METAL = ITEMS.register("imagiphase_metal",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> IMAGIPHASE_METAL_BLOCK = ITEMS.register("imagiphase_metal_block",
            () -> new BlockItem(Blocks.IMAGIPHASE_METAL_BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<Item, EmptyUnitItem> EMPTY_UNIT = ITEMS.register("empty_unit",
            EmptyUnitItem::new);
    public static final DeferredHolder<Item, ImagiphaseUnitItem> IMAGIPHASE_UNIT = ITEMS.register("imagiphase_unit",
            ImagiphaseUnitItem::new);
    public static final DeferredHolder<Item, Item> WIND_GEN_BASE_SCREEN = ITEMS.register("wind_gen_base_screen",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> IMAGIPHASE_CIRCUIT = ITEMS.register("imagiphase_circuit",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> IMAGIPHASE_POLYMER = ITEMS.register("imagiphase_polymer",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> IMAGIPHASE_PLATE = ITEMS.register("imagiphase_plate",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, ImagiphaseDowsingRodItem> IMAGIPHASE_DOWSING_ROD = ITEMS.register("imagiphase_dowsing_rod",
            ImagiphaseDowsingRodItem::new);
    public static final DeferredHolder<Item, BlockItem> IMAGIPHASE_VEGETATION = ITEMS.register("imagiphase_vegetation",
            () -> new BlockItem(Blocks.IMAGIPHASE_VEGETATION.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> IMAGIPHASE_LEAVES = ITEMS.register("imagiphase_leaves",
            () -> new BlockItem(Blocks.IMAGIPHASE_LEAVES.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> IMAGIPHASE_LOG = ITEMS.register("imagiphase_log",
            () -> new BlockItem(Blocks.IMAGIPHASE_LOG.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> IMAGIPHASE_LICHEN = ITEMS.register("imagiphase_lichen",
            () -> new BlockItem(Blocks.IMAGIPHASE_LICHEN.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> OMNI_CRAFTING_TABLE = ITEMS.register("omni_crafting_table",
            () -> new BlockItem(Blocks.OMNI_CRAFTING_TABLE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> CAT_ENGINE = ITEMS.register("cat_engine",
            () -> new BlockItem(Blocks.CAT_ENGINE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> IMAGIPHASE_AMETHYST = ITEMS.register("imagiphase_amethyst",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> IMAGIPHASE_AMETHYST_BLOCK = ITEMS.register("imagiphase_amethyst_block",
            () -> new BlockItem(Blocks.IMAGIPHASE_AMETHYST_BLOCK.get(), new Item.Properties()));

    private Items() {
    }
}