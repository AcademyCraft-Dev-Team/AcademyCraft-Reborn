package org.academy.api.common.ability.event;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.academy.api.common.ability.Skill;

/**
 * Fired after built-in CP modifiers and before CP occupation. Subscribers may
 * change the final pre-intensity cost or cancel the cast.
 */
public final class SkillExecutionCostEvent extends Event implements ICancellableEvent {
    private final Skill.ActiveCostContext context;
    private final boolean continuous;
    private float cost;

    public SkillExecutionCostEvent(
            Skill.ActiveCostContext context,
            boolean continuous,
            float cost
    ) {
        this.context = context;
        this.continuous = continuous;
        this.cost = cost;
    }

    public Skill.ActiveCostContext context() {
        return context;
    }

    public boolean continuous() {
        return continuous;
    }

    public float cost() {
        return cost;
    }

    public void setCost(float cost) {
        this.cost = cost;
    }
}
