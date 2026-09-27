package org.academy.internal.common.ability.accelerator.program;

/**
 * Player-selectable intent tier. The server runtime maps it to skill level, proficiency, CP cost,
 * velocity and damage bounds; it is never a raw gameplay multiplier.
 */
public enum AcceleratorProgramStrength {
    CONTROLLED(0),
    STANDARD(1),
    MAXIMUM(2);

    private final int wireId;

    AcceleratorProgramStrength(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public static AcceleratorProgramStrength byWireId(int wireId) {
        return switch (wireId) {
            case 0 -> CONTROLLED;
            case 1 -> STANDARD;
            case 2 -> MAXIMUM;
            default -> throw new IllegalArgumentException(
                    "Unknown accelerator program strength " + wireId);
        };
    }
}
