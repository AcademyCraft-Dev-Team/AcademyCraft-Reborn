package org.academy.internal.common.ability.accelerator.reflection.compat;

public record VectorIncomingDamageResult(Status status, float remainingDamage) {
    public static VectorIncomingDamageResult passThrough(float damage) {
        return new VectorIncomingDamageResult(Status.PASS_THROUGH, damage);
    }

    public static VectorIncomingDamageResult fullRedirect() {
        return new VectorIncomingDamageResult(Status.FULL_REDIRECT, 0.0f);
    }

    public static VectorIncomingDamageResult partial(float remainingDamage) {
        return new VectorIncomingDamageResult(Status.PARTIAL_REFLECTION, Math.max(0.0f, remainingDamage));
    }

    public boolean handled() {
        return status != Status.PASS_THROUGH;
    }

    public enum Status {
        PASS_THROUGH,
        FULL_REDIRECT,
        PARTIAL_REFLECTION
    }
}
