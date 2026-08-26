package org.academy.internal.common.world.item;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import org.academy.internal.common.world.level.material.Fluids;

public final class EmptyUnitItem extends Item {
    public EmptyUnitItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        var hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hitResult.getType() == HitResult.Type.MISS) {
            return InteractionResult.PASS;
        }
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        }

        var pos = hitResult.getBlockPos();
        var state = level.getBlockState(pos);
        if (!level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, hitResult.getDirection(), stack)
                || !state.getFluidState().isSourceOfType(Fluids.IMAG_PHASE.get())
                || !(state.getBlock() instanceof BucketPickup bucketPickup)) {
            return InteractionResult.FAIL;
        }

        var filled = bucketPickup.pickupBlock(player, level, pos, state);
        if (filled.isEmpty()) {
            return InteractionResult.FAIL;
        }

        bucketPickup.getPickupSound(state).ifPresent(sound -> player.playSound(sound, 1.0F, 1.0F));
        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        var result = player.hasInfiniteMaterials()
                ? stack
                : ItemUtils.createFilledResult(stack, player, filled);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, filled);
        }
        return InteractionResult.SUCCESS.heldItemTransformedTo(result);
    }
}
