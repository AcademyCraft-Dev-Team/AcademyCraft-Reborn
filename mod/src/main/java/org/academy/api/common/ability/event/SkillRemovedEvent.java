package org.academy.api.common.ability.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class SkillRemovedEvent extends PlayerEvent {
    private final String skillKey;

    public SkillRemovedEvent(ServerPlayer player, String skillKey) {
        super(player);
        this.skillKey = skillKey;
    }

    public String getSkillKey() {
        return skillKey;
    }
}
