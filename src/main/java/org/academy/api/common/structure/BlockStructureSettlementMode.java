package org.academy.api.common.structure;

/** Determines how one captured cell leaves the structure entity when motion ends. */
public enum BlockStructureSettlementMode {
    /** Restore the cell directly on the nearest grid without applying falling-block physics. */
    FIXED,
    /** Emit the cell as a vanilla falling block so natural terrain can settle flat. */
    FALLING
}
