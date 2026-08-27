package org.academy.internal.common.world.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.academy.internal.common.world.entity.projectile.PaperAirplane;

public final class PaperAirplaneItem extends Item {
    private static final float THROW_SPEED = 0.45f;

    public PaperAirplaneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel) {
            var airplane = new PaperAirplane(serverLevel, player);
            airplane.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, THROW_SPEED, 0.0f);
            serverLevel.addFreshEntity(airplane);
            serverLevel.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT,
                    SoundSource.PLAYERS, 0.45f, 1.25f);
            if (!player.hasInfiniteMaterials()) stack.shrink(1);
            player.getCooldowns().addCooldown(stack, 5);
        }
        return InteractionResult.SUCCESS;
    }
}
