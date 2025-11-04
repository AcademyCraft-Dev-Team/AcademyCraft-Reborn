/*
package org.academy.internal.common.world.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import org.academy.internal.common.world.level.material.Fluids;

public final class ImagiphaseUnitItem extends Item {
    public ImagiphaseUnitItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var itemStack = player.getItemInHand(hand);
        var blockHitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (blockHitResult.getType() == HitResult.Type.MISS) {
            return InteractionResult.PASS;
        } else if (blockHitResult.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        } else {
            var direction = blockHitResult.getDirection();
            var blockPos = blockHitResult.getBlockPos().relative(direction);
            if (!level.mayInteract(player, blockPos) || !player.mayUseItemAt(blockPos, direction, itemStack)) {
                return InteractionResult.FAIL;
            } else {
                var blockState = level.getBlockState(blockPos);
                if (blockState.canBeReplaced()) {
                    level.setBlock(blockPos, Fluids.IMAGIPHASE_PLASMA.get().defaultFluidState().createLegacyBlock(), 11);

                    if (player instanceof ServerPlayer serverPlayer) {
                        CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, blockPos, itemStack);
                    }

                    var soundevent = SoundEvents.BUCKET_EMPTY;
                    level.playSound(player, blockPos, soundevent, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.gameEvent(player, GameEvent.FLUID_PLACE, blockPos);

                    player.awardStat(Stats.ITEM_USED.get(this));
                    return InteractionResult.SUCCESS;
                } else {
                    return InteractionResult.FAIL;
                }
            }
        }
    }

*/
/*
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand usedHand) {

    }*//*

}*/
