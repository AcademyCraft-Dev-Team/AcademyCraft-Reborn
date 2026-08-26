package org.academy.internal.common.world.item;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import org.academy.internal.common.world.level.material.Fluids;

public final class ImagPhaseUnitItem extends Item {
    public ImagPhaseUnitItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        var hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hitResult.getType() == HitResult.Type.MISS) {
            return InteractionResult.PASS;
        }
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        }

        var direction = hitResult.getDirection();
        var pos = hitResult.getBlockPos().relative(direction);
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, direction, stack)) {
            return InteractionResult.FAIL;
        }
        if (!level.getBlockState(pos).canBeReplaced(Fluids.IMAG_PHASE.get())) {
            return InteractionResult.FAIL;
        }

        if (!level.setBlock(pos, Fluids.IMAG_PHASE.get().defaultFluidState().createLegacyBlock(), 11)) {
            return InteractionResult.FAIL;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, pos, stack);
        }
        level.playSound(player, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
        player.awardStat(Stats.ITEM_USED.get(this));

        var result = player.hasInfiniteMaterials()
                ? stack
                : ItemUtils.createFilledResult(stack, player, new ItemStack(Items.EMPTY_UNIT.get()));
        return InteractionResult.SUCCESS.heldItemTransformedTo(result);
    }
}
