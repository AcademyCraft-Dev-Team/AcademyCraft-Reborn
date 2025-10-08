package org.academy.internal.common.world.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.internal.common.world.level.block.Blocks;

import static org.academy.AcademyCraft.MODID;

@SuppressWarnings("unused")
public final class Items {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredHolder<Item, Item> ICON =
            ITEMS.registerItem("icon", Item::new);
    public static final DeferredHolder<Item, DataTerminalItem> DATA_TERMINAL =
            ITEMS.registerItem("data_terminal", DataTerminalItem::new);
    public static final DeferredHolder<Item, CoinItem> COIN =
            ITEMS.registerItem("coin", CoinItem::new);
    public static final DeferredHolder<Item, BlockItem> WIRELESS_NODE =
            ITEMS.registerSimpleBlockItem("wireless_node", Blocks.WIRELESS_NODE, new Item.Properties());
    public static final DeferredHolder<Item, BlockItem> WIND_GEN_BASE =
            ITEMS.registerSimpleBlockItem("wind_gen_base", Blocks.WIND_GEN_BASE, new Item.Properties());
    public static final DeferredHolder<Item, BlockItem> WIND_GEN_TOP =
            ITEMS.registerSimpleBlockItem("wind_gen_top", Blocks.WIND_GEN_TOP, new Item.Properties());
    public static final DeferredHolder<Item, BlockItem> WIND_GEN_PILLAR =
            ITEMS.registerSimpleBlockItem("wind_gen_pillar", Blocks.WIND_GEN_PILLAR, new Item.Properties());
    public static final DeferredHolder<Item, MultiBlockItem> ABILITY_DEVELOPER =
            ITEMS.registerItem("ability_developer", properties -> new MultiBlockItem(Blocks.ABILITY_DEVELOPER.get(), properties));
    public static final DeferredHolder<Item, Item> WIND_GEN_FAN_ITEM =
            ITEMS.registerItem("wind_gen_fan", properties -> new Item(properties.stacksTo(16)));
    public static final DeferredHolder<Item, Item> IMAGIPHASE_METAL =
            ITEMS.registerItem("imagiphase_metal", Item::new);
    public static final DeferredHolder<Item, BlockItem> IMAGIPHASE_METAL_BLOCK =
            ITEMS.registerSimpleBlockItem("imagiphase_metal_block", Blocks.IMAGIPHASE_METAL_BLOCK, new Item.Properties());
    public static final DeferredHolder<Item, EmptyUnitItem> EMPTY_UNIT =
            ITEMS.registerItem("empty_unit", EmptyUnitItem::new);
    public static final DeferredHolder<Item, ImagiphaseUnitItem> IMAGIPHASE_UNIT =
            ITEMS.registerItem("imagiphase_unit", ImagiphaseUnitItem::new);
    public static final DeferredHolder<Item, Item> SCREEN =
            ITEMS.registerItem("screen", Item::new);
    public static final DeferredHolder<Item, Item> IMAGIPHASE_CIRCUIT =
            ITEMS.registerItem("imagiphase_circuit", Item::new);
    public static final DeferredHolder<Item, Item> IMAGIPHASE_POLYMER =
            ITEMS.registerItem("imagiphase_polymer", Item::new);
    public static final DeferredHolder<Item, Item> IMAGIPHASE_PLATE =
            ITEMS.registerItem("imagiphase_plate", Item::new);
    public static final DeferredHolder<Item, ImagiphaseDowsingRodItem> IMAGIPHASE_DOWSING_ROD =
            ITEMS.registerItem("imagiphase_dowsing_rod", ImagiphaseDowsingRodItem::new);
    public static final DeferredHolder<Item, BlockItem> IMAGIPHASE_VEGETATION =
            ITEMS.registerSimpleBlockItem("imagiphase_vegetation", Blocks.IMAGIPHASE_VEGETATION, new Item.Properties());
    public static final DeferredHolder<Item, BlockItem> IMAGIPHASE_LEAVES =
            ITEMS.registerSimpleBlockItem("imagiphase_leaves", Blocks.IMAGIPHASE_LEAVES, new Item.Properties());
    public static final DeferredHolder<Item, BlockItem> IMAGIPHASE_LOG =
            ITEMS.registerSimpleBlockItem("imagiphase_log", Blocks.IMAGIPHASE_LOG, new Item.Properties());
    public static final DeferredHolder<Item, BlockItem> IMAGIPHASE_LICHEN =
            ITEMS.registerSimpleBlockItem("imagiphase_lichen", Blocks.IMAGIPHASE_LICHEN, new Item.Properties());
    public static final DeferredHolder<Item, BlockItem> OMNI_CRAFTING_TABLE =
            ITEMS.registerSimpleBlockItem("omni_crafting_table", Blocks.OMNI_CRAFTING_TABLE, new Item.Properties());
    public static final DeferredHolder<Item, BlockItem> CAT_ENGINE =
            ITEMS.registerSimpleBlockItem("cat_engine", Blocks.CAT_ENGINE, new Item.Properties());
    public static final DeferredHolder<Item, Item> IMAGIPHASE_AMETHYST =
            ITEMS.registerItem("imagiphase_amethyst", Item::new);
    public static final DeferredHolder<Item, BlockItem> IMAGIPHASE_AMETHYST_BLOCK =
            ITEMS.registerSimpleBlockItem("imagiphase_amethyst_block", Blocks.IMAGIPHASE_AMETHYST_BLOCK, new Item.Properties());
    public static final DeferredHolder<Item, BlockItem> IMAGIPHASE_AMETHYST_CLUSTER =
            ITEMS.registerSimpleBlockItem("imagiphase_amethyst_cluster", Blocks.IMAGIPHASE_AMETHYST_CLUSTER, new Item.Properties());
    public static final DeferredHolder<Item, BlockItem> BUDDING_IMAGIPHASE_AMETHYST =
            ITEMS.registerSimpleBlockItem("budding_imagiphase_amethyst", Blocks.BUDDING_IMAGIPHASE_AMETHYST, new Item.Properties());
    public static final DeferredHolder<Item, BlockItem> LARGE_IMAGIPHASE_AMETHYST_BUD =
            ITEMS.registerSimpleBlockItem("large_imagiphase_amethyst_bud", Blocks.LARGE_IMAGIPHASE_AMETHYST_BUD, new Item.Properties());
    public static final DeferredHolder<Item, BlockItem> MEDIUM_IMAGIPHASE_AMETHYST_BUD =
            ITEMS.registerSimpleBlockItem("medium_imagiphase_amethyst_bud", Blocks.MEDIUM_IMAGIPHASE_AMETHYST_BUD, new Item.Properties());
    public static final DeferredHolder<Item, BlockItem> SMALL_IMAGIPHASE_AMETHYST_BUD =
            ITEMS.registerSimpleBlockItem("small_imagiphase_amethyst_bud", Blocks.SMALL_IMAGIPHASE_AMETHYST_BUD, new Item.Properties());
    public static final DeferredHolder<Item, BlockItem> SOLAR_GEN =
            ITEMS.registerSimpleBlockItem("solar_gen", Blocks.SOLAR_GEN, new Item.Properties());

    private Items() {
    }
}