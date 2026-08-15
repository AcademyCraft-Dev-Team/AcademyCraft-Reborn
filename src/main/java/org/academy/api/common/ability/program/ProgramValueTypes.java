package org.academy.api.common.ability.program;

import org.academy.AcademyCraft;

/**
 * Value types provided by the shared ability-program runtime.
 */
public final class ProgramValueTypes {
    public static final ProgramValueType FLOW = type("flow");
    public static final ProgramValueType BOOLEAN = type("boolean");
    public static final ProgramValueType INTEGER = type("integer");
    public static final ProgramValueType BIG_INTEGER = type("big_integer");
    public static final ProgramValueType FLOAT = type("float");
    public static final ProgramValueType IDENTIFIER = type("identifier");
    public static final ProgramValueType DURATION = type("duration");
    public static final ProgramValueType DIRECTION = type("direction");
    public static final ProgramValueType DIRECTION_SET = type("direction_set");
    public static final ProgramValueType CONTROL_DESTINATION = type("control_destination");
    public static final ProgramValueType WORLD_POSITION = type("world_position");
    public static final ProgramValueType BLOCK_POSITION = type("block_position");
    public static final ProgramValueType ENTITY_REFERENCE = type("entity_reference");
    public static final ProgramValueType LIVING_ENTITY_REFERENCE = type("living_entity_reference");
    public static final ProgramValueType WORLD_POSITION_SET = type("world_position_set");
    public static final ProgramValueType BLOCK_POSITION_SET = type("block_position_set");
    public static final ProgramValueType ENTITY_SET = type("entity_set");
    public static final ProgramValueType LIVING_ENTITY_SET = type("living_entity_set");
    public static final ProgramValueType ACTION_RESULT = type("action_result");

    private ProgramValueTypes() {
    }

    /**
     * Returns whether an output of {@code source} can be connected to an input of {@code target}.
     * Potentially lossy world conversions intentionally require explicit graph nodes.
     */
    public static boolean canConnect(ProgramValueType source, ProgramValueType target) {
        if (source.equals(target)) return true;
        if (source.equals(INTEGER) && target.equals(FLOAT)) return true;
        if (source.equals(LIVING_ENTITY_REFERENCE) && target.equals(ENTITY_REFERENCE)) return true;
        if (target.equals(CONTROL_DESTINATION)) {
            return source.equals(WORLD_POSITION)
                    || source.equals(BLOCK_POSITION)
                    || source.equals(ENTITY_REFERENCE)
                    || source.equals(LIVING_ENTITY_REFERENCE);
        }
        return source.equals(LIVING_ENTITY_SET) && target.equals(ENTITY_SET);
    }

    private static ProgramValueType type(String path) {
        return new ProgramValueType(AcademyCraft.academy("program_type/" + path));
    }
}
