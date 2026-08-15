package org.academy.api.common.ability.program;

import java.util.Map;

/**
 * Non-semantic editor data. Changes to this record must not invalidate compiled bytecode.
 */
public record ProgramEditorLayout(Map<Integer, NodePosition> nodePositions) {
    public static final ProgramEditorLayout EMPTY = new ProgramEditorLayout(Map.of());

    public ProgramEditorLayout {
        nodePositions = nodePositions == null ? Map.of() : Map.copyOf(nodePositions);
    }

    public record NodePosition(double x, double y) {
        public NodePosition {
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                throw new IllegalArgumentException("Program node position must be finite");
            }
        }
    }
}
