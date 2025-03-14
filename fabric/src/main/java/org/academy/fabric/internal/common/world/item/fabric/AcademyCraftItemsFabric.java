package org.academy.fabric.internal.common.world.item.fabric;

import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.item.AcademyCraftItems;

public class AcademyCraftItemsFabric {
    public static final AbilityDeveloperBlockItem ABILITY_DEVELOPER_BLOCK_ITEM = new AbilityDeveloperBlockItem();

    public static void init() {
        AcademyCraftItems.ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "ability_developer_block_item"), ABILITY_DEVELOPER_BLOCK_ITEM);
    }

    private AcademyCraftItemsFabric() {
    }
}
