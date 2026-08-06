package org.academy.internal.common.ability.mentalout.precision;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record CompiledPrecisionProgram(
        PrecisionGraph graph,
        List<PrecisionGraph.Node> order,
        List<PrecisionGraph.Node> dataOrder,
        List<PrecisionGraph.Node> actionOrder,
        Map<InputKey, PrecisionGraph.Edge> inputs
) {
    public static CompileResult compile(PrecisionGraph graph) {
        var validation = graph == null
                ? PrecisionGraph.EMPTY.validate()
                : graph.validate();
        if (!validation.valid()) {
            return new CompileResult(null, validation.diagnostic());
        }
        if (validation.normalized().nodes().isEmpty()) {
            return new CompileResult(null, PrecisionGraph.Diagnostic.EMPTY_PROGRAM);
        }
        var byId = new HashMap<Integer, PrecisionGraph.Node>();
        validation.normalized().nodes().forEach(node -> byId.put(node.id(), node));
        var dataOrder = validation.topologicalOrder().stream()
                .map(byId::get)
                .filter(node -> !node.kind().isAction())
                .toList();
        var actionOrder = validation.actionOrder().stream().map(byId::get).toList();
        var order = new java.util.ArrayList<PrecisionGraph.Node>(dataOrder.size() + actionOrder.size());
        order.addAll(dataOrder);
        order.addAll(actionOrder);
        var inputs = new HashMap<InputKey, PrecisionGraph.Edge>();
        validation.normalized().edges().forEach(edge ->
                inputs.put(new InputKey(edge.toNode(), edge.toPort()), edge));
        return new CompileResult(
                new CompiledPrecisionProgram(
                        validation.normalized(),
                        List.copyOf(order),
                        List.copyOf(dataOrder),
                        List.copyOf(actionOrder),
                        Map.copyOf(inputs)
                ),
                PrecisionGraph.Diagnostic.OK
        );
    }

    public PrecisionGraph.Edge input(int nodeId, int port) {
        return inputs.get(new InputKey(nodeId, port));
    }

    public record InputKey(int nodeId, int port) {
    }

    public record CompileResult(CompiledPrecisionProgram program, PrecisionGraph.Diagnostic diagnostic) {
        public boolean valid() {
            return program != null && diagnostic == PrecisionGraph.Diagnostic.OK;
        }
    }
}
