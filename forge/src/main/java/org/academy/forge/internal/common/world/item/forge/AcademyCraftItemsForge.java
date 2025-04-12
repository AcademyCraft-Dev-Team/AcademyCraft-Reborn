package org.academy.forge.internal.common.world.item.forge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.academy.AcademyCraft;
import org.academy.forge.internal.common.world.level.block.entity.forge.AbilityDeveloperBlockEntityForge;
import org.academy.forge.internal.common.world.level.block.forge.AcademyCraftBlocksForge;
import org.academy.internal.common.world.item.AbilityDeveloperComputationalChipItem;
import org.academy.internal.common.world.item.AcademyCraftItems;

public class AcademyCraftItemsForge {
    public static final AbilityDeveloperBlockItemForge ABILITY_DEVELOPER_BLOCK_ITEM = new AbilityDeveloperBlockItemForge();
    public static final BlockItem RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE_BLOCK_ITEM = new BlockItem(AcademyCraftBlocksForge.RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE_BLOCK, new Item.Properties());

    private AcademyCraftItemsForge() {
    }

    public static void init() {
        AcademyCraftItems.ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "ability_developer_block_item"), ABILITY_DEVELOPER_BLOCK_ITEM);
        AcademyCraftItems.ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "radio_frequency_energy_output_bridge_block_item"), RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE_BLOCK_ITEM);
        AbilityDeveloperComputationalChipItem.itemInterface = (level, player, blockHitResult) -> {
            if (level.getBlockEntity(blockHitResult.getBlockPos()) instanceof AbilityDeveloperBlockEntityForge abilityDeveloperBlockEntityForge) {
                if (abilityDeveloperBlockEntityForge.isEmpty()) {
                    final ItemStack itemStack = player.getMainHandItem();
                    final ItemStack newItemStack = itemStack.copy();
                    abilityDeveloperBlockEntityForge.setItem(0, newItemStack);
                    itemStack.shrink(1);
                }
            }
        };
    }
}
