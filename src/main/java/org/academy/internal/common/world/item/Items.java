package org.academy.internal.common.world.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.internal.common.world.level.block.Blocks;

import static org.academy.AcademyCraft.MODID;

public final class Items {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredHolder<Item, Item> ICON =
            ITEMS.registerItem("icon", Item::new);
    public static final DeferredHolder<Item, DataTerminalItem> DATA_TERMINAL =
            ITEMS.registerItem("data_terminal", DataTerminalItem::new);
    public static final DeferredHolder<Item, CoinItem> COIN =
            ITEMS.registerItem("coin", CoinItem::new);
    public static final DeferredHolder<Item, BlockItem> WIRELESS_NODE =
            ITEMS.registerSimpleBlockItem("wireless_node", Blocks.WIRELESS_NODE);
    public static final DeferredHolder<Item, BlockItem> WIND_GEN_BASE =
            ITEMS.registerSimpleBlockItem("wind_gen_base", Blocks.WIND_GEN_BASE);
    public static final DeferredHolder<Item, BlockItem> WIND_GEN_TOP =
            ITEMS.registerSimpleBlockItem("wind_gen_top", Blocks.WIND_GEN_TOP);
    public static final DeferredHolder<Item, BlockItem> WIND_GEN_PILLAR =
            ITEMS.registerSimpleBlockItem("wind_gen_pillar", Blocks.WIND_GEN_PILLAR);
    public static final DeferredHolder<Item, MultiBlockItem> ABILITY_DEVELOPER =
            ITEMS.registerItem("ability_developer",
                    properties -> new MultiBlockItem(Blocks.ABILITY_DEVELOPER.get(), properties)
            );
    public static final DeferredHolder<Item, Item> WIND_GEN_FAN_ITEM =
            ITEMS.registerItem("wind_gen_fan",
                    properties -> new Item(properties.stacksTo(16))
            );
    public static final DeferredHolder<Item, MultiBlockItem> OMNI_CRAFTING_TABLE =
            ITEMS.registerItem("omni_crafting_table",
                    properties -> new MultiBlockItem(Blocks.OMNI_CRAFTING_TABLE.get(), properties)
            );
    public static final DeferredHolder<Item, BlockItem> CAT_ENGINE =
            ITEMS.registerSimpleBlockItem("cat_engine", Blocks.CAT_ENGINE);
    public static final DeferredHolder<Item, BlockItem> SOLAR_GEN =
            ITEMS.registerSimpleBlockItem("solar_gen", Blocks.SOLAR_GEN);

    private Items() {
    }
}