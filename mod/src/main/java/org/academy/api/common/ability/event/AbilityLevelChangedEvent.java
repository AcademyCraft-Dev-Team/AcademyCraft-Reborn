package org.academy.api.common.ability.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.academy.api.common.ability.AbilityCategory;

public class AbilityLevelChangedEvent extends PlayerEvent {
    private final AbilityCategory category;
    private final int level;

    public AbilityLevelChangedEvent(ServerPlayer player, AbilityCategory category, int level) {
        super(player);
        this.category = category;
        this.level = level;
    }

    public AbilityCategory getCategory() {
        return category;
    }

    public int getLevel() {
        return level;
    }
}
