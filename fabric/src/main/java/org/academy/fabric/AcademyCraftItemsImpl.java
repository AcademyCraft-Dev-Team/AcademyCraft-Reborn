package org.academy.fabric;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.item.fabric.AbilityDeveloperBlockItem;

import java.util.Map;

public class AcademyCraftItemsImpl {
    public static final AbilityDeveloperBlockItem ABILITY_DEVELOPER_BLOCK_ITEM = new AbilityDeveloperBlockItem();

    public static void init(Map<ResourceLocation, Item> itemMap) {
        itemMap.put(new ResourceLocation(AcademyCraft.MOD_ID, "ability_developer_block_item"), ABILITY_DEVELOPER_BLOCK_ITEM);
    }

    private AcademyCraftItemsImpl() {
    }
}
