package org.academy.api.common.ability.event;

import net.neoforged.bus.api.Event;
import org.academy.api.common.ability.Skill;

/**
 * Fired after CP has been occupied and immediately before the skill action.
 */
public final class SkillExecutionStartEvent extends Event {
    private final Skill.ActiveExecutionContext context;
    private final boolean continuous;

    public SkillExecutionStartEvent(Skill.ActiveExecutionContext context, boolean continuous) {
        this.context = context;
        this.continuous = continuous;
    }

    public Skill.ActiveExecutionContext context() {
        return context;
    }

    public boolean continuous() {
        return continuous;
    }
}
