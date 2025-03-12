package org.academy.internal.common.world.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.academy.AcademyCraft;
import org.jetbrains.annotations.NotNull;

public class DataTerminalItem extends Item {
    public DataTerminalItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand interactionHand) {
        AcademyCraft.LOGGER.info("Player " + player.getName() + " used Data Terminal Item");
        return InteractionResultHolder.consume(player.getItemInHand(interactionHand));
    }
}