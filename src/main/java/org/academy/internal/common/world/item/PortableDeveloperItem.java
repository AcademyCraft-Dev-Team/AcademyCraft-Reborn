package org.academy.internal.common.world.item;

import icyllis.modernui.mc.MuiModApi;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.academy.AcademyCraft;
import org.academy.internal.client.ui.PhoneFragment;
import org.jetbrains.annotations.NotNull;

public class PortableDeveloperItem extends Item {
    public PortableDeveloperItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand interactionHand) {
        if (level.isClientSide()) {
            openScreen();
        } else {
            AcademyCraft.LOGGER.info("Player {} used Portable Developer Item", player.getName());
        }
        return InteractionResultHolder.consume(player.getItemInHand(interactionHand));
    }

    private void openScreen() {
        MuiModApi.openScreen(new PhoneFragment());
    }
}