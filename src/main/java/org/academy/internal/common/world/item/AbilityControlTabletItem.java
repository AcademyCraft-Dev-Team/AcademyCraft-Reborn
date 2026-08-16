package org.academy.internal.common.world.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.academy.api.common.ability.DevelopmentSource;
import org.academy.api.common.energy.AcademyEnergyItem;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;

public final class AbilityControlTabletItem extends Item implements AcademyEnergyItem {
    public static final int ENERGY_CAPACITY = AbilityDeveloperBlockEntity.MAX_ENERGY_STORAGE / 4;

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

    public static int storedEnergy(ItemStack stack) {
        if (stack == null || !stack.is(Items.ABILITY_CONTROL_TABLET.get())) return 0;
        return Math.clamp(stack.getOrDefault(ItemDataComponents.ENERGY.get(), 0), 0, ENERGY_CAPACITY);
    }

    public static void setStoredEnergy(ItemStack stack, int energy) {
        if (stack == null || !stack.is(Items.ABILITY_CONTROL_TABLET.get())) return;
        stack.set(ItemDataComponents.ENERGY.get(), Math.clamp(energy, 0, ENERGY_CAPACITY));
    }

    @Override
    public int getEnergyStored(ItemStack stack) {
        return storedEnergy(stack);
    }

    @Override
    public int getMaxEnergyStored(ItemStack stack) {
        return ENERGY_CAPACITY;
    }

    @Override
    public int receiveEnergy(ItemStack stack, int maxReceive, boolean simulate) {
        if (maxReceive <= 0 || stack == null || !stack.is(this)) return 0;
        var stored = storedEnergy(stack);
        var received = Math.min(maxReceive, ENERGY_CAPACITY - stored);
        if (!simulate && received > 0) setStoredEnergy(stack, stored + received);
        return received;
    }

    @Override
    public int extractEnergy(ItemStack stack, int maxExtract, boolean simulate) {
        if (maxExtract <= 0 || stack == null || !stack.is(this)) return 0;
        var stored = storedEnergy(stack);
        var extracted = Math.min(maxExtract, stored);
        if (!simulate && extracted > 0) setStoredEnergy(stack, stored - extracted);
        return extracted;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * storedEnergy(stack) / ENERGY_CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x55FF55;
    }
}
