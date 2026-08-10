package org.academy.internal.common.ability.mentalout.precision;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record CompiledPrecisionProgram(
        PrecisionGraph graph,
        List<PrecisionGraph.Node> order,
        List<PrecisionGraph.Node> dataOrder,
        List<PrecisionGraph.Node> actionOrder,
        Map<InputKey, PrecisionGraph.Edge> inputs,
        Map<FlowKey, Integer> flowTargets
) {
    public static CompileResult compile(PrecisionGraph graph) {
        var validation = graph == null
                ? PrecisionGraph.EMPTY.validate()
                : graph.validate();
        if (!validation.valid()) {
            return new CompileResult(null, validation.diagnostic(), validation.nodeId(), validation.port());
        }
        if (validation.normalized().nodes().isEmpty()) {
            return new CompileResult(null, PrecisionGraph.Diagnostic.EMPTY_PROGRAM, -1, -1);
        }
        var byId = new HashMap<Integer, PrecisionGraph.Node>();
        validation.normalized().nodes().forEach(node -> byId.put(node.id(), node));
        var dataOrder = validation.topologicalOrder().stream()
                .map(byId::get)
                .filter(node -> !node.kind().isAction())
                .toList();
        var actionOrder = validation.actionOrder().stream().map(byId::get).toList();
        var order = new ArrayList<PrecisionGraph.Node>(dataOrder.size() + actionOrder.size());
        order.addAll(dataOrder);
        order.addAll(actionOrder);
        var inputs = new HashMap<InputKey, PrecisionGraph.Edge>();
        var flowTargets = new HashMap<FlowKey, Integer>();
        validation.normalized().edges().forEach(edge ->
        {
            inputs.put(new InputKey(edge.toNode(), edge.toPort()), edge);
            var source = byId.get(edge.fromNode());
            if (source != null && edge.fromPort() >= 0
                    && edge.fromPort() < source.kind().outputDefinitions().size()
                    && source.kind().outputDefinitions().get(edge.fromPort()).type()
                    == PrecisionGraph.PortType.FLOW) {
                flowTargets.put(new FlowKey(edge.fromNode(), edge.fromPort()), edge.toNode());
            }
        });
        return new CompileResult(
                new CompiledPrecisionProgram(
                        validation.normalized(),
                        List.copyOf(order),
                        List.copyOf(dataOrder),
                        List.copyOf(actionOrder),
                        Map.copyOf(inputs),
                        Map.copyOf(flowTargets)
                ),
                PrecisionGraph.Diagnostic.OK,
                -1,
                -1
        );
    }

    public PrecisionGraph.Edge input(int nodeId, int port) {
        return inputs.get(new InputKey(nodeId, port));
    }

    public Integer flowTarget(int nodeId, int outputPort) {
        return flowTargets.get(new FlowKey(nodeId, outputPort));
    }

    public record InputKey(int nodeId, int port) {
    }

    public record FlowKey(int nodeId, int port) {
    }

    public record CompileResult(
            CompiledPrecisionProgram program,
            PrecisionGraph.Diagnostic diagnostic,
            int nodeId,
            int port
    ) {
        public boolean valid() {
            return program != null && diagnostic == PrecisionGraph.Diagnostic.OK;
        }
    }
}
