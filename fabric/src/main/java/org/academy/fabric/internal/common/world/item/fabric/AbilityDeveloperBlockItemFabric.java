package org.academy.fabric.internal.common.world.item.fabric;

import net.minecraft.world.item.Item;
import org.academy.fabric.internal.common.world.level.block.fabric.AcademyCraftBlocksFabric;
import org.academy.internal.common.world.item.AbilityDeveloperBlockItem;

public class AbilityDeveloperBlockItemFabric extends AbilityDeveloperBlockItem {
    public AbilityDeveloperBlockItemFabric() {
        super(AcademyCraftBlocksFabric.ABILITY_DEVELOPER_BLOCK, new Item.Properties());
    }
}