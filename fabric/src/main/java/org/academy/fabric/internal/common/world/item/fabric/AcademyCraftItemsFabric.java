package org.academy.fabric.internal.common.world.item.fabric;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.academy.AcademyCraft;
import org.academy.fabric.internal.common.world.level.block.entity.fabric.AbilityDeveloperBlockEntityFabric;
import org.academy.fabric.internal.common.world.level.block.fabric.AcademyCraftBlocksFabric;
import org.academy.internal.common.world.item.AbilityDeveloperComputationalChipItem;
import org.academy.internal.common.world.item.AcademyCraftItems;

public class AcademyCraftItemsFabric {
    public static final BlockItem ABILITY_DEVELOPER_BLOCK_ITEM = new AbilityDeveloperBlockItem();
    public static final BlockItem RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE_BLOCK_ITEM = new BlockItem(AcademyCraftBlocksFabric.RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE_BLOCK, new Item.Properties());

    private AcademyCraftItemsFabric() {
    }

    public static void init() {
        AcademyCraftItems.ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "ability_developer_block_item"), ABILITY_DEVELOPER_BLOCK_ITEM);
        AcademyCraftItems.ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "radio_frequency_energy_output_bridge_block_item"), RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE_BLOCK_ITEM);
        AbilityDeveloperComputationalChipItem.itemInterface = (level, player, blockHitResult) -> {
            if (level.getBlockEntity(blockHitResult.getBlockPos()) instanceof AbilityDeveloperBlockEntityFabric abilityDeveloperBlockEntity) {
                if (abilityDeveloperBlockEntity.isEmpty()) {
                    final ItemStack itemStack = player.getMainHandItem();
                    final ItemStack newItemStack = itemStack.copy();
                    abilityDeveloperBlockEntity.setItem(0, newItemStack);
                    itemStack.shrink(1);
                }
            }
        };
    }
}
