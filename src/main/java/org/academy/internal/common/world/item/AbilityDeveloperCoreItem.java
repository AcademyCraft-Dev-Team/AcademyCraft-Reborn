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
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.jetbrains.annotations.NotNull;

public class AbilityDeveloperCoreItem extends Item {
    public AbilityDeveloperCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand usedHand) {
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                HitResult hitResult;
                if (player.isCreative()) {
                    hitResult = player.pick(5, 1.0F, false);
                } else {
                    hitResult = player.pick(4.5f, 1.0F, false);
                }
                if (hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                    if (level.getBlockState(blockHitResult.getBlockPos()).hasBlockEntity()) {
                        if (level.getBlockEntity(blockHitResult.getBlockPos()) instanceof AbilityDeveloperBlockEntity abilityDeveloperBlockEntity) {
                            if (abilityDeveloperBlockEntity.isEmpty()) {
                                abilityDeveloperBlockEntity.setItem(0, player.getMainHandItem());
                                player.getMainHandItem().shrink(1);
                            }
                        }
                    }
                }
                return new InteractionResultHolder<>(InteractionResult.CONSUME, player.getItemInHand(usedHand));
            }
        }
        return super.use(level, player, usedHand);
    }
}