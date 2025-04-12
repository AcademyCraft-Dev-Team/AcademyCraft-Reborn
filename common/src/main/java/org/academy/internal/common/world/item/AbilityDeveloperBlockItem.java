package org.academy.internal.common.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class AbilityDeveloperBlockItem extends BlockItem {
    protected AbilityDeveloperBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean canPlace(BlockPlaceContext context, @NotNull BlockState state) {
        BlockPos basePos = context.getClickedPos();
        Level level = context.getLevel();
        CollisionContext collisionContext = context.getPlayer() == null ? CollisionContext.empty() : CollisionContext.of(context.getPlayer());

        List<BlockPos> requiredPositions = AbilityDeveloperBlock.getRotatedSubjectBlocks(basePos, context.getHorizontalDirection());
        requiredPositions.add(basePos);

        for (BlockPos pos : requiredPositions) {
            BlockState existingState = level.getBlockState(pos);

            BlockPlaceContext simulatedContext = new BlockPlaceContext(new UseOnContext(
                    context.getLevel(),
                    context.getPlayer(),
                    context.getHand(),
                    context.getItemInHand(),
                    new BlockHitResult(
                            context.getClickLocation(),
                            context.getClickedFace(),
                            pos,
                            false
                    )
            ));

            boolean canReplace = existingState.canBeReplaced(simulatedContext);

            if (!canReplace || !level.isUnobstructed(state, pos, collisionContext)) {
                return false;
            }
        }
        return super.canPlace(context, state);
    }
}