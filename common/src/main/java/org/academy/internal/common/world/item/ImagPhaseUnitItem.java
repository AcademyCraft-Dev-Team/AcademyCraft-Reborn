package org.academy.internal.common.world.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.academy.internal.common.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;

public class ImagPhaseUnitItem extends Item {
    public ImagPhaseUnitItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);
        BlockHitResult blockHitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (blockHitResult.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(itemStack);
        } else if (blockHitResult.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(itemStack);
        } else {
            Direction direction = blockHitResult.getDirection();
            BlockPos blockPos = blockHitResult.getBlockPos().relative(direction);
            if (!level.mayInteract(player, blockPos) || !player.mayUseItemAt(blockPos, direction, itemStack)) {
                return InteractionResultHolder.fail(itemStack);
            } else {
                BlockState blockState = level.getBlockState(blockPos);
                if (blockState.canBeReplaced()) {
                    level.setBlock(blockPos, Fluids.IMAG_PHASE.defaultFluidState().createLegacyBlock(), 11);

                    if (player instanceof ServerPlayer serverPlayer) {
                        CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, blockPos, itemStack);
                    }

                    SoundEvent soundevent = SoundEvents.BUCKET_EMPTY;
                    level.playSound(player, blockPos, soundevent, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.gameEvent(player, GameEvent.FLUID_PLACE, blockPos);

                    player.awardStat(Stats.ITEM_USED.get(this));
                    return InteractionResultHolder.sidedSuccess(!player.getAbilities().instabuild ? new ItemStack(Items.EMPTY_UNIT) : itemStack, level.isClientSide());
                } else {
                    return InteractionResultHolder.fail(itemStack);
                }
            }
        }
    }
}