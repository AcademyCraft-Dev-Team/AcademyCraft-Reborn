package org.academy.api.common.ability.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.academy.api.common.ability.AbilityCategory;

public class AbilityCategoryChangedEvent extends PlayerEvent {
    private final AbilityCategory category;
    private final boolean skillsCleared;

    public AbilityCategoryChangedEvent(ServerPlayer player, AbilityCategory category, boolean skillsCleared) {
        super(player);
        this.category = category;
        this.skillsCleared = skillsCleared;
    }

    public AbilityCategory getCategory() {
        return category;
    }

    public boolean isSkillsCleared() {
        return skillsCleared;
    }
}
