package org.academy.api.common.ability.event;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class AbilityOverloadEvent extends PlayerEvent {
    public AbilityOverloadEvent(ServerPlayer player) {
        super(player);
    }
}