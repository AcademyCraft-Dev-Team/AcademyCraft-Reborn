package org.academy.api.common.ability.event;

import net.neoforged.bus.api.Event;
import org.academy.api.common.ability.Skill;

/** Fired after the skill action, including when the action throws. */
public final class SkillExecutionFinishEvent extends Event {
    private final Skill.ActiveExecutionContext context;
    private final boolean continuous;
    private final boolean successful;
    private final Throwable failure;

    public SkillExecutionFinishEvent(
            Skill.ActiveExecutionContext context,
            boolean continuous,
            boolean successful,
            Throwable failure
    ) {
        this.context = context;
        this.continuous = continuous;
        this.successful = successful;
        this.failure = failure;
    }

    public Skill.ActiveExecutionContext context() {
        return context;
    }

    public boolean continuous() {
        return continuous;
    }

    public boolean successful() {
        return successful;
    }

    public Throwable failure() {
        return failure;
    }
}
