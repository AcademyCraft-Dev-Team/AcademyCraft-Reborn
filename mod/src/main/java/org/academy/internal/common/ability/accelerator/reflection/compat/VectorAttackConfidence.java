package org.academy.internal.common.ability.accelerator.reflection.compat;

public enum VectorAttackConfidence {
    NONE(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    EXACT(4);

    private final int rank;

    VectorAttackConfidence(int rank) {
        this.rank = rank;
    }

    public boolean atLeast(VectorAttackConfidence other) {
        return rank >= other.rank;
    }
}
