package org.academy.internal.common.world.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.academy.internal.client.app.tutorial.TutorialScreen;

public final class TutorialItem extends Item {
    public TutorialItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) TutorialScreen.open();
        return InteractionResult.SUCCESS;
    }
}
