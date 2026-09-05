package org.academy.internal.common.world.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public final class MisakaTowerItem extends Item {
    public MisakaTowerItem(Properties properties) {
        super(properties
                .component(DataComponents.FOOD, new FoodProperties(8, 0.6f, true))
                .component(DataComponents.CONSUMABLE, Consumables.DEFAULT_FOOD));
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.accept(Component.translatable("item.academy.misaka_tower.tooltip.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.academy.misaka_tower.tooltip.2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.academy.misaka_tower.tooltip.3")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
