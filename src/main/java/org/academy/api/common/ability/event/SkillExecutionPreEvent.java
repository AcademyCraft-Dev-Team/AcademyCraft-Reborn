package org.academy.api.common.ability.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.academy.api.common.ability.Skill;

/**
 * Fired before a skill calculates or occupies CP.
 */
public final class SkillExecutionPreEvent extends Event implements ICancellableEvent {
    private final Skill skill;
    private final ServerPlayer player;
    private final boolean continuous;

    public SkillExecutionPreEvent(Skill skill, ServerPlayer player, boolean continuous) {
        this.skill = skill;
        this.player = player;
        this.continuous = continuous;
    }

    public Skill skill() {
        return skill;
    }

    public ServerPlayer player() {
        return player;
    }

    public boolean continuous() {
        return continuous;
    }
}
