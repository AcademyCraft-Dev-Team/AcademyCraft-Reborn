package org.academy.internal.common.world.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.academy.internal.common.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

public class EmptyUnitItem extends Item {
    public EmptyUnitItem() {
        super(new Properties().stacksTo(16));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        BlockHitResult blockhitresult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);

        if (blockhitresult.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(itemstack);
        } else if (blockhitresult.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(itemstack);
        } else {
            BlockPos targetedPos = blockhitresult.getBlockPos();
            Direction direction = blockhitresult.getDirection();

            if (level.mayInteract(player, targetedPos) && player.mayUseItemAt(targetedPos, direction, itemstack)) {
                if (level.getBlockState(targetedPos).getBlock() == Blocks.IMAG_PHASE) {
                    level.setBlock(targetedPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 11);
                    level.gameEvent(player, GameEvent.FLUID_PICKUP, targetedPos);

                    ItemStack result = new ItemStack(Items.IMAG_PHASE_UNIT);

                    if (!player.getAbilities().instabuild) {
                        itemstack.shrink(1);
                    }

                    if (!level.isClientSide) {
                        CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayer) player, result);
                    }

                    if (itemstack.isEmpty()) {
                        return InteractionResultHolder.sidedSuccess(result, level.isClientSide());
                    } else {
                        if (!player.getInventory().add(result)) {
                            player.drop(result, false);
                        }
                        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
                    }
                }
            }
            return InteractionResultHolder.fail(itemstack);
        }
    }
}