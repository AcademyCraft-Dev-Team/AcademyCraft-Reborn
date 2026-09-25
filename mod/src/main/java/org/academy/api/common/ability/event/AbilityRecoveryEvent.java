package org.academy.api.common.ability.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class AbilityRecoveryEvent extends PlayerEvent {
    public AbilityRecoveryEvent(ServerPlayer serverPlayer) {
        super(serverPlayer);
    }
}
