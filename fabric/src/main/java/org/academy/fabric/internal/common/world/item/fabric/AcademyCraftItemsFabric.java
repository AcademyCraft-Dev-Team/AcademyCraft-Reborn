package org.academy.fabric.internal.common.world.item.fabric;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import org.academy.AcademyCraft;
import org.academy.fabric.internal.common.world.level.block.fabric.AcademyCraftBlocksFabric;
import org.academy.internal.common.world.item.Items;

public class AcademyCraftItemsFabric {
    public static final BlockItem ABILITY_DEVELOPER_BLOCK_ITEM = new AbilityDeveloperBlockItemFabric();
    public static final BlockItem RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE_BLOCK_ITEM = new BlockItem(AcademyCraftBlocksFabric.RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE_BLOCK, new Item.Properties());

    private AcademyCraftItemsFabric() {
    }

    public static void init() {
        Items.ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "ability_developer_block"), ABILITY_DEVELOPER_BLOCK_ITEM);
        Items.ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "radio_frequency_energy_output_bridge_block"), RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE_BLOCK_ITEM);
    }
}