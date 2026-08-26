package org.academy.internal.common.world.item;

import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.darkmatter.DarkmatterShape;

import static net.minecraft.core.registries.Registries.BLOCK;

public final class DarkmatterToolItem extends DarkmatterEquipmentItem {
    public static final TagKey<Block> EFFECTIVE_BLOCKS = TagKey.create(
            BLOCK, AcademyCraft.academy("mineable/darkmatter_tool"));

    public DarkmatterToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public DarkmatterShape darkmatterShape() {
        return DarkmatterShape.TOOL;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!DarkmatterItemUtil.isOperational(context.getItemInHand())) return InteractionResult.PASS;
        for (var delegate : new Item[]{
                net.minecraft.world.item.Items.NETHERITE_AXE,
                net.minecraft.world.item.Items.NETHERITE_SHOVEL,
                net.minecraft.world.item.Items.NETHERITE_HOE}) {
            var result = delegate.useOn(context);
            if (result.consumesAction()) return result;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean canPerformAction(ItemInstance stack, ItemAbility action) {
        if (stack instanceof ItemStack itemStack
                && !DarkmatterItemUtil.isOperational(itemStack)) return false;
        return ItemAbilities.DEFAULT_AXE_ACTIONS.contains(action)
                || ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(action)
                || ItemAbilities.DEFAULT_HOE_ACTIONS.contains(action)
                || super.canPerformAction(stack, action);
    }
}
