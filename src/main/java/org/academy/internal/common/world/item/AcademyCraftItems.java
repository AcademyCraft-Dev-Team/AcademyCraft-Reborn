package org.academy.internal.common.world.item;

import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

public class AcademyCraftItems {
    public static final List<Item> ITEM_LIST = new ArrayList<>();
    public static final AcademyCraftIconItem ACADEMY_CRAFT_ICON_ITEM = new AcademyCraftIconItem();
    public static final PortableDeveloperItem DEVELOPER_PORTABLE_ITEM = new PortableDeveloperItem();
    public static final DataTerminalItem DATA_TERMINAL_ITEM = new DataTerminalItem();
    public static final CoinItem COIN_ITEM = new CoinItem();

    static {
        ITEM_LIST.add(ACADEMY_CRAFT_ICON_ITEM);
        ITEM_LIST.add(DEVELOPER_PORTABLE_ITEM);
        ITEM_LIST.add(DATA_TERMINAL_ITEM);
        ITEM_LIST.add(COIN_ITEM);
    }

    private AcademyCraftItems() {
    }
}
