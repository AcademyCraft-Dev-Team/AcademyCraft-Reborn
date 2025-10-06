package org.academy.internal.common.world.item;

import net.minecraft.world.item.Item;

public class ImagiphaseUnitItem extends Item {
    public ImagiphaseUnitItem(Properties properties) {
        super(properties.stacksTo(16));
    }


/*
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
                    level.setBlock(blockPos, Fluids.IMAGIPHASE_PLASMA.get().defaultFluidState().createLegacyBlock(), 11);

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
    }*/
}