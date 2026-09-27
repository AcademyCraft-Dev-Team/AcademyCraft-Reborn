package org.academy.api.common.ability;

public enum ProficiencyEvent {
    KILL_ENTITY(4.0f),
    ACTIVE_TICK(0.1f),
    EFFECTIVE_TICK(0.2f),
    TRIGGER(2.0f),
    PASSIVE_TICK(0.01f);

    private final float increment;

    ProficiencyEvent(float increment) {
        this.increment = increment;
    }

    public float getIncrement() {
        return increment;
    }
}
