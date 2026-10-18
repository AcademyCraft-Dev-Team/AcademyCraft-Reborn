package org.academy.api.common.ability.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.academy.api.common.ability.AbilityCategory;

public class SkillLearnedEvent extends PlayerEvent {
    private final AbilityCategory category;
    private final String skillKey;

    public SkillLearnedEvent(ServerPlayer player, AbilityCategory category, String skillKey) {
        super(player);
        this.category = category;
        this.skillKey = skillKey;
    }

    public AbilityCategory getCategory() {
        return category;
    }

    public String getSkillKey() {
        return skillKey;
    }
}
