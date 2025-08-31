package org.academy.internal.common.world.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import org.academy.internal.common.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

public class EmptyUnitItem extends Item {
    public EmptyUnitItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        var itemstack = player.getItemInHand(hand);
        var blockhitresult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);

        if (blockhitresult.getType() == HitResult.Type.MISS) {
            return InteractionResult.PASS;
        } else if (blockhitresult.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        } else {
            var targetedPos = blockhitresult.getBlockPos();
            var direction = blockhitresult.getDirection();

            if (level.mayInteract(player, targetedPos) && player.mayUseItemAt(targetedPos, direction, itemstack)) {
                if (level.getBlockState(targetedPos).getBlock() == Blocks.IMAGIPHASE_PLASMA.get()) {
                    level.setBlock(targetedPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 11);
                    level.gameEvent(player, GameEvent.FLUID_PICKUP, targetedPos);

                    var result = new ItemStack(Items.EMPTY_UNIT);

                    if (!player.getAbilities().instabuild) {
                        itemstack.shrink(1);
                    }

                    if (!level.isClientSide) {
                        CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayer) player, result);
                    }

                    if (itemstack.isEmpty()) {
                        return InteractionResult.SUCCESS;
                    } else {
                        if (!player.getInventory().add(result)) {
                            player.drop(result, false);
                        }
                        return InteractionResult.SUCCESS;
                    }
                }
            }
            return InteractionResult.FAIL;
        }
    }
}