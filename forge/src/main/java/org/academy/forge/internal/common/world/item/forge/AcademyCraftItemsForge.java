package org.academy.forge.internal.common.world.item.forge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.item.AcademyCraftItems;
import org.academy.forge.internal.common.world.level.block.entity.forge.AbilityDeveloperBlockEntity;
import org.academy.internal.common.world.item.AbilityDeveloperComputationalChipItem;

public class AcademyCraftItemsForge {
    public static final AbilityDeveloperBlockItem ABILITY_DEVELOPER_BLOCK_ITEM = new AbilityDeveloperBlockItem();

    public static void init() {
        AcademyCraftItems.ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "ability_developer_block_item"), ABILITY_DEVELOPER_BLOCK_ITEM);
        AbilityDeveloperComputationalChipItem.itemInterface = (level, player, blockHitResult) -> {
            if (level.getBlockEntity(blockHitResult.getBlockPos()) instanceof AbilityDeveloperBlockEntity abilityDeveloperBlockEntity) {
                if (abilityDeveloperBlockEntity.isEmpty()) {
                    final ItemStack itemStack = player.getMainHandItem();
                    final ItemStack newItemStack = itemStack.copy();
                    abilityDeveloperBlockEntity.setItem(0, newItemStack);
                    itemStack.shrink(1);
                }
            }
        };
    }

    private AcademyCraftItemsForge() {
    }
}
