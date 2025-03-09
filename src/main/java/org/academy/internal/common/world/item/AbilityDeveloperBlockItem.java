package org.academy.internal.common.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.academy.internal.common.world.level.block.AcademyCraftBlocks;
import org.jetbrains.annotations.NotNull;

public class AbilityDeveloperBlockItem extends BlockItem {
    public AbilityDeveloperBlockItem() {
        super(AcademyCraftBlocks.ABILITY_DEVELOPER_BLOCK, new Item.Properties());
    }

    @Override
    protected boolean canPlace(BlockPlaceContext context, @NotNull BlockState state) {
        final BlockPos pos = context.getClickedPos();
        final Level level = context.getLevel();
        final CollisionContext collisionContext = context.getPlayer() == null ? CollisionContext.empty() : CollisionContext.of(context.getPlayer());
        final boolean canPlace = AbilityDeveloperBlock.getRotatedSubjectBlocks(pos, context.getHorizontalDirection()).stream().allMatch(blockPos -> level.getBlockState(blockPos).isAir() && level.isUnobstructed(state, blockPos, collisionContext));
        return canPlace && super.canPlace(context, state) && level.getBlockState(pos).isAir();
    }
}