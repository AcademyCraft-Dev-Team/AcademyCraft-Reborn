package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.ProgramInputView;
import org.academy.api.common.ability.program.ProgramValue;
import org.academy.api.common.ability.program.ProgramValueTypes;
import org.academy.internal.common.ability.program.compile.CompiledProgram;

import java.util.ArrayList;
import java.util.List;

/** Bounded, scalar-only explanation of the flow taken by one invocation. */
public final class ProgramRunTrace {
    public static final int MAX_STEPS = 48;
    public static final int MAX_DETAIL_LENGTH = 120;
    private final List<Step> steps = new ArrayList<>();
    private boolean truncated;

    public record Step(int nodeId, String flowOutput, String detail) {
        public Step {
            if (nodeId < 0) throw new IllegalArgumentException("Invalid trace node");
            flowOutput = bounded(flowOutput, 32);
            detail = bounded(detail, MAX_DETAIL_LENGTH);
        }
    }

    public void record(int nodeId, String flowOutput, String detail) {
        if (steps.size() == MAX_STEPS) {
            truncated = true;
            return;
        }
        steps.add(new Step(nodeId, flowOutput, detail));
    }

    public void record(CompiledProgram.CompiledNode node, String flowOutput, ProgramInputView inputs) {
        var detail = new StringBuilder();
        for (var port : node.schema().inputs()) {
            if (port.type().equals(ProgramValueTypes.FLOW)) continue;
            var value = inputs.first(port.name()).orElse(null);
            if (value == null) continue;
            var scalar = scalar(value);
            if (scalar == null) continue;
            if (!detail.isEmpty()) detail.append(", ");
            detail.append(port.name()).append('=').append(scalar);
            if (detail.length() >= MAX_DETAIL_LENGTH) break;
        }
        record(node.id(), flowOutput, detail.toString());
    }

    public List<Step> steps() {
        return List.copyOf(steps);
    }

    public boolean truncated() {
        return truncated;
    }

    private static String scalar(ProgramValue<?> value) {
        var raw = value.value();
        if (raw instanceof Boolean || raw instanceof Number) return raw.toString();
        if (raw instanceof String text) return '"' + bounded(text, 48) + '"';
        return null;
    }

    private static String bounded(String raw, int limit) {
        if (raw == null) return "";
        var normalized = raw.replace('\n', ' ').replace('\r', ' ');
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit - 1) + "…";
    }
}
