package org.academy.internal.common.world.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;

public class AbilityDeveloperComputationalChipItem extends Item {
    public static AbilityDeveloperComputationalChipItemInterface itemInterface;

    public AbilityDeveloperComputationalChipItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand usedHand) {
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                final HitResult hitResult;
                if (player.isCreative()) {
                    hitResult = player.pick(5, 1.0F, false);
                } else {
                    hitResult = player.pick(4.5f, 1.0F, false);
                }
                if (hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                    if (level.getBlockState(blockHitResult.getBlockPos()).hasBlockEntity()) {
                        itemInterface.run(level, player, blockHitResult);
                    }
                }
                return new InteractionResultHolder<>(InteractionResult.CONSUME, player.getItemInHand(usedHand));
            }
        }
        return new InteractionResultHolder<>(InteractionResult.SUCCESS, player.getItemInHand(usedHand));
    }

    public interface AbilityDeveloperComputationalChipItemInterface {
        void run(@NotNull Level level, @NotNull Player player, @NotNull BlockHitResult blockHitResult);
    }
}