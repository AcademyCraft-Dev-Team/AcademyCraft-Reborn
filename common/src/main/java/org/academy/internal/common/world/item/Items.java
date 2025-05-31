package org.academy.internal.common.world.item;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import org.academy.internal.common.world.level.material.Fluids;

import java.util.HashMap;
import java.util.Map;

import static net.minecraft.world.item.Items.BUCKET;

@SuppressWarnings("unused")
public class Items {
    public static final Map<String, Item> ITEMS = new HashMap<>();
    public static final Item ICON = register("icon", new IconItem());
    public static final Item DATA_TERMINAL = register("data_terminal", new DataTerminalItem());
    public static final Item COIN = register("coin", new CoinItem());
    public static final Item WIRELESS_NODE_BLOCK = register("wireless_node_block", new WirelessNodeBlockItem());
    public static final Item WIND_GEN_BASE_BLOCK = register("wind_gen_base_block", new WindGenBaseBlockItem());
    public static final Item WIND_GEN_TOP_BLOCK = register("wind_gen_top_block", new WindGenTopBlockItem());
    public static final Item WIND_GEN_PILLAR_BLOCK = register("wind_gen_pillar_block", new WindGenPillarBlockItem());
    public static final Item ABILITY_DEVELOPER_BLOCK = register("ability_developer_block", new AbilityDeveloperBlockItem());
    public static final Item WIND_GEN_FAN_ITEM = register("wind_gen_fan", new WindGenFanItem());
    public static final Item IMAG_PHASE_BUCKET = register("imag_phase_bucket", new BucketItem(Fluids.IMAG_PHASE, (new Item.Properties()).craftRemainder(BUCKET).stacksTo(1)));

    public static <T extends Item> T register(String name, T item) {
        ITEMS.put(name, item);
        return item;
    }

    private Items() {
    }
}