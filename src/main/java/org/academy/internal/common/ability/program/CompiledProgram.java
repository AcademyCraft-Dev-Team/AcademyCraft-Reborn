package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.api.common.ability.program.ProgramNodeRole;
import org.academy.api.common.ability.program.ProgramNodeSchema;
import org.academy.api.common.ability.program.ProgramNodeType;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Registry-resolved intermediate representation consumed by the metered VM.
 */
public record CompiledProgram(
        ProgramGraph graph,
        int entryNodeId,
        Map<Integer, CompiledNode> nodes,
        List<Integer> dataOrder,
        Map<InputKey, List<OutputKey>> inputs,
        Map<FlowKey, Integer> flowTargets
) {
    public CompiledProgram {
        nodes = Map.copyOf(nodes);
        dataOrder = List.copyOf(dataOrder);
        inputs = inputs.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> List.copyOf(entry.getValue())
        ));
        flowTargets = Map.copyOf(flowTargets);
    }

    public List<OutputKey> inputs(int nodeId, String port) {
        return inputs.getOrDefault(new InputKey(nodeId, port), List.of());
    }

    public Integer flowTarget(int nodeId, String port) {
        return flowTargets.get(new FlowKey(nodeId, port));
    }

    public record CompiledNode(
            int id,
            Identifier typeId,
            ProgramNodeType<?> type,
            Object configuration,
            ProgramNodeRole role,
            ProgramNodeSchema schema
    ) {
    }

    public record InputKey(int nodeId, String port) {
    }

    public record OutputKey(int nodeId, String port) {
    }

    public record FlowKey(int nodeId, String port) {
    }
}
