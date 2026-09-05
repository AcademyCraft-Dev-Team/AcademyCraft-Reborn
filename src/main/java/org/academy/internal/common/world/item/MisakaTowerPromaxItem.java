package org.academy.internal.common.world.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public final class MisakaTowerPromaxItem extends Item {
    public MisakaTowerPromaxItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.accept(Component.translatable("item.academy.misaka_tower_promax.tooltip.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.academy.misaka_tower_promax.tooltip.2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.academy.misaka_tower_promax.tooltip.3")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
