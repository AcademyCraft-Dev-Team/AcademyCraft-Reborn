package org.academy.internal.common.world.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.academy.api.common.ability.DevelopmentSource;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;

public final class AbilityControlTabletItem extends Item {
    public static final int ENERGY_CAPACITY = 1_440_000;

    public AbilityControlTabletItem(Properties properties) {
        super(properties.stacksTo(1).component(ItemDataComponents.ENERGY.get(), 0));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            AbilityDeveloperBlock.openScreen(serverPlayer, DevelopmentSource.tablet(hand));
        }
        return InteractionResult.SUCCESS;
    }

    public static int getEnergyStored(ItemStack stack) {
        if (stack == null || !stack.is(Items.ABILITY_CONTROL_TABLET.get())) return 0;
        return Math.clamp(stack.getOrDefault(ItemDataComponents.ENERGY.get(), 0), 0, ENERGY_CAPACITY);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * getEnergyStored(stack) / ENERGY_CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x55FF55;
    }
}
