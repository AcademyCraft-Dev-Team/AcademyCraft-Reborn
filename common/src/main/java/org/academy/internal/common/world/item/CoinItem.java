package org.academy.internal.common.world.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.academy.internal.common.world.entity.AcademyCraftEntityTypes;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.jetbrains.annotations.NotNull;

public class CoinItem extends Item {
    public CoinItem() {
        super(new Properties());
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand interactionHand) {
        ItemStack itemStack = player.getItemInHand(interactionHand);
        if (!level.isClientSide) {
            itemStack.hurtAndBreak(1, player, playerx -> playerx.broadcastBreakEvent(player.getUsedItemHand()));

            if (!player.isCreative()) {
                itemStack.setCount(itemStack.getCount() - 1);
            }
            ThrownCoin thrownCoin = new ThrownCoin(AcademyCraftEntityTypes.THROWN_COIN_ENTITY_TYPE, level);
            thrownCoin.setPos(player.position().x, player.position().y + 1, player.position().z);
            thrownCoin.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 0.65F, 1.0F);
            thrownCoin.pickup = AbstractArrow.Pickup.ALLOWED;
            level.addFreshEntity(thrownCoin);

            player.playSound(AcademyCraftSoundEvents.COIN);
        }
        return InteractionResultHolder.consume(itemStack);
    }
}