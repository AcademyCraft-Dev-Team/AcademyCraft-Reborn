package org.academy.api.common.ability.program;

/**
 * Describes when a node may observe or mutate game state.
 */
public enum ProgramNodePurity {
    PURE,
    /**
     * Mutates only the current program session, never world state.
     */
    STATE,
    WORLD_QUERY,
    ACTION,
    SUSPEND
}
