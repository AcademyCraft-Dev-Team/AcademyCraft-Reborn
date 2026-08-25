package org.academy.api.common.ability.program;

/** Vertical reference point used when converting an entity into a world position. */
public enum ProgramEntityPositionAnchor {
    FEET("feet"),
    CENTER("center"),
    EYES("eyes");

    private final String wireName;

    ProgramEntityPositionAnchor(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static ProgramEntityPositionAnchor byName(String value) {
        for (var anchor : values()) {
            if (anchor.wireName.equals(value)) return anchor;
        }
        throw new IllegalArgumentException("Unknown entity position anchor " + value);
    }
}
