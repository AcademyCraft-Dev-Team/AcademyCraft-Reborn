package org.academy.internal.common.world.item;

import net.minecraft.world.item.Item;

public class DataTerminalItem extends Item {
    public DataTerminalItem(Properties properties) {
        super(properties.stacksTo(1));
    }
}