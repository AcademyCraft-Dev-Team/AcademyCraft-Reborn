package org.academy.internal.common.ability.mentalout.precision;

import org.academy.api.common.entitycontrol.ControlCapability;

import java.util.*;

public record PrecisionGraph(List<Node> nodes, List<Edge> edges) {
    public static final int MAX_NODES = 32;
    public static final int MAX_DATA_EDGES = 48;
    public static final int MAX_FLOW_EDGES = MAX_NODES - 1;
    public static final int MAX_EDGES = MAX_DATA_EDGES + MAX_FLOW_EDGES;
    public static final int MAX_ENCODED_BYTES = 16 * 1024;
    public static final PrecisionGraph EMPTY = new PrecisionGraph(List.of(), List.of());

    public PrecisionGraph {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }

    public static Migration migrateLegacy(PrecisionGraph graph) {
        if (graph == null || graph.nodes.isEmpty()) return new Migration(EMPTY, Diagnostic.OK);
        var legacy = graph.validate(false);
        if (!legacy.valid) return new Migration(EMPTY, legacy.diagnostic);
        var byId = new HashMap<Integer, Node>();
        legacy.normalized.nodes.forEach(node -> byId.put(node.id, node));
        var actions = legacy.topologicalOrder.stream()
                .map(byId::get)
                .filter(node -> node.kind.isAction())
                .toList();
        var migratedEdges = new ArrayList<>(legacy.normalized.edges);
        for (var index = 1; index < actions.size(); index++) {
            var previous = actions.get(index - 1);
            var next = actions.get(index);
            migratedEdges.add(new Edge(
                    previous.id,
                    previous.kind.flowOutputPort(),
                    next.id,
                    next.kind.flowInputPort()
            ));
        }
        var migrated = new PrecisionGraph(legacy.normalized.nodes, migratedEdges);
        var validation = migrated.validate();
        return validation.valid
                ? new Migration(validation.normalized, Diagnostic.OK)
                : new Migration(EMPTY, validation.diagnostic);
    }

    private static int findFlowCycleNode(Set<Integer> actionIds, Map<Integer, Integer> flowTargets) {
        var complete = new HashSet<Integer>();
        for (var start : actionIds) {
            if (complete.contains(start)) continue;
            var path = new HashSet<Integer>();
            var current = start;
            while (current != null && actionIds.contains(current) && !complete.contains(current)) {
                if (!path.add(current)) return current;
                current = flowTargets.get(current);
            }
            complete.addAll(path);
        }
        return -1;
    }

    private static Integer flowTarget(
            int source,
            Map<Integer, Node> byId,
            Map<Integer, Map<Integer, Edge>> incoming
    ) {
        for (var entry : incoming.entrySet()) {
            var node = byId.get(entry.getKey());
            if (node == null || !node.kind.isAction()) continue;
            var edge = entry.getValue().get(node.kind.flowInputPort());
            if (edge != null && edge.fromNode == source) return node.id;
        }
        return null;
    }

    public static boolean isPortCompatible(PortType output, PortType input) {
        return output == input || output == PortType.ENTITY && input == PortType.DESTINATION;
    }

    public Validation validate() {
        return validate(true);
    }

    private Validation validate(boolean requireFlow) {
        if (nodes.size() > MAX_NODES) return Validation.error(Diagnostic.TOO_MANY_NODES);
        if (edges.size() > MAX_EDGES) return Validation.error(Diagnostic.TOO_MANY_EDGES);

        var byId = new HashMap<Integer, Node>();
        var actionIds = new HashSet<Integer>();
        for (var node : nodes) {
            if (node == null || node.kind == null || node.id < 0 || node.id > 1_000_000) {
                return Validation.error(Diagnostic.INVALID_NODE, node == null ? -1 : node.id, -1);
            }
            if (!Double.isFinite(node.parameter) || !Double.isFinite(node.x) || !Double.isFinite(node.y)) {
                return Validation.error(Diagnostic.NON_FINITE_VALUE, node.id, -1);
            }
            if (byId.putIfAbsent(node.id, node) != null) {
                return Validation.error(Diagnostic.DUPLICATE_NODE, node.id, -1);
            }
            if (!node.kind.isParameterValid(node.parameter)) {
                return Validation.error(Diagnostic.INVALID_PARAMETER, node.id, -1);
            }
            if (node.kind.isAction()) actionIds.add(node.id);
        }
        if (!nodes.isEmpty() && actionIds.isEmpty()) return Validation.error(Diagnostic.NO_ACTION);

        var incoming = new HashMap<Integer, Map<Integer, Edge>>();
        var outgoing = new HashMap<Integer, List<Integer>>();
        var flowIncoming = new HashMap<Integer, Integer>();
        var flowOutgoing = new HashMap<Integer, Integer>();
        var flowTargets = new HashMap<Integer, Integer>();
        var indegree = new HashMap<Integer, Integer>();
        for (var node : nodes) indegree.put(node.id, 0);
        var uniqueEdges = new HashSet<Edge>();
        var dataEdges = 0;
        var flowEdges = 0;
        for (var edge : edges) {
            if (edge == null || !uniqueEdges.add(edge)) {
                return Validation.error(
                        Diagnostic.DUPLICATE_EDGE,
                        edge == null ? -1 : edge.toNode,
                        edge == null ? -1 : edge.toPort
                );
            }
            var from = byId.get(edge.fromNode);
            var to = byId.get(edge.toNode);
            if (from == null || to == null || edge.fromNode == edge.toNode) {
                return Validation.error(Diagnostic.INVALID_EDGE, to == null ? -1 : to.id, edge.toPort);
            }
            if (edge.fromPort < 0 || edge.fromPort >= from.kind.outputs.size()
                    || edge.toPort < 0 || edge.toPort >= to.kind.inputs.size()) {
                return Validation.error(Diagnostic.INVALID_PORT, to.id, edge.toPort);
            }
            var output = from.kind.outputDefinitions.get(edge.fromPort);
            var input = to.kind.inputDefinitions.get(edge.toPort);
            if (!isPortCompatible(output.type, input.type)) {
                return Validation.error(Diagnostic.TYPE_MISMATCH, to.id, edge.toPort);
            }
            var ports = incoming.computeIfAbsent(edge.toNode, _ -> new HashMap<>());
            if (ports.putIfAbsent(edge.toPort, edge) != null) {
                return Validation.error(Diagnostic.MULTIPLE_INPUTS, to.id, edge.toPort);
            }
            if (output.type == PortType.FLOW) {
                flowEdges++;
                if (!from.kind.isAction() || !to.kind.isAction()) {
                    return Validation.error(Diagnostic.INVALID_FLOW, to.id, edge.toPort);
                }
                if (flowOutgoing.merge(from.id, 1, Integer::sum) > 1) {
                    return Validation.error(Diagnostic.BRANCHED_FLOW, from.id, edge.fromPort);
                }
                if (flowIncoming.merge(to.id, 1, Integer::sum) > 1) {
                    return Validation.error(Diagnostic.BRANCHED_FLOW, to.id, edge.toPort);
                }
                flowTargets.put(from.id, to.id);
            } else {
                dataEdges++;
            }
            outgoing.computeIfAbsent(edge.fromNode, _ -> new ArrayList<>()).add(edge.toNode);
            indegree.compute(edge.toNode, (_, value) -> value == null ? 1 : value + 1);
        }
        if (dataEdges > MAX_DATA_EDGES || flowEdges > MAX_FLOW_EDGES) {
            return Validation.error(Diagnostic.TOO_MANY_EDGES);
        }
        var flowCycleNode = findFlowCycleNode(actionIds, flowTargets);
        if (flowCycleNode >= 0) {
            return Validation.error(Diagnostic.FLOW_CYCLE, flowCycleNode, -1);
        }

        for (var node : nodes) {
            var connected = incoming.getOrDefault(node.id, Map.of());
            for (var port = 0; port < node.kind.inputDefinitions.size(); port++) {
                var definition = node.kind.inputDefinitions.get(port);
                if (definition.required && !connected.containsKey(port)) {
                    return Validation.error(Diagnostic.MISSING_INPUT, node.id, port);
                }
            }
        }

        var ready = new PriorityQueue<Integer>();
        indegree.forEach((id, degree) -> {
            if (degree == 0) ready.add(id);
        });
        var order = new ArrayList<Integer>(nodes.size());
        while (!ready.isEmpty()) {
            var id = ready.remove();
            order.add(id);
            for (var target : outgoing.getOrDefault(id, List.of())) {
                var next = indegree.computeIfPresent(target, (_, degree) -> degree - 1);
                if (next != null && next == 0) ready.add(target);
            }
        }
        if (order.size() != nodes.size()) {
            var ordered = Set.copyOf(order);
            var cycleNode = nodes.stream().mapToInt(Node::id).filter(id -> !ordered.contains(id)).min().orElse(-1);
            return Validation.error(Diagnostic.CYCLE, cycleNode, -1);
        }

        var actionOrder = new ArrayList<Integer>();
        if (requireFlow && !actionIds.isEmpty()) {
            if (flowEdges != actionIds.size() - 1) {
                return Validation.error(Diagnostic.DISCONNECTED_FLOW,
                        actionIds.stream().mapToInt(Integer::intValue).min().orElse(-1), -1);
            }
            var roots = actionIds.stream().filter(id -> !flowIncoming.containsKey(id)).toList();
            if (roots.size() != 1) {
                return Validation.error(Diagnostic.DISCONNECTED_FLOW,
                        actionIds.stream().mapToInt(Integer::intValue).min().orElse(-1), -1);
            }
            var current = roots.getFirst();
            var visited = new HashSet<Integer>();
            while (visited.add(current)) {
                actionOrder.add(current);
                var next = flowTarget(current, byId, incoming);
                if (next == null) break;
                current = next;
            }
            if (visited.size() != actionIds.size()) {
                var disconnected = actionIds.stream().filter(id -> !visited.contains(id))
                        .mapToInt(Integer::intValue).min().orElse(current);
                return Validation.error(Diagnostic.DISCONNECTED_FLOW, disconnected, -1);
            }
        } else {
            order.stream().filter(actionIds::contains).forEach(actionOrder::add);
        }

        var normalizedNodes = nodes.stream()
                .map(Node::normalized)
                .sorted(Comparator.comparingInt(Node::id))
                .toList();
        var normalizedEdges = edges.stream()
                .sorted(Comparator.comparingInt(Edge::toNode)
                        .thenComparingInt(Edge::toPort)
                        .thenComparingInt(Edge::fromNode)
                        .thenComparingInt(Edge::fromPort))
                .toList();
        return new Validation(
                true,
                Diagnostic.OK,
                -1,
                -1,
                new PrecisionGraph(normalizedNodes, normalizedEdges),
                List.copyOf(order),
                List.copyOf(actionOrder)
        );
    }

    public FlowPosition flowPosition(int nodeId) {
        var node = nodes.stream().filter(candidate -> candidate.id == nodeId).findFirst().orElse(null);
        if (node == null || !node.kind.isAction()) return FlowPosition.NOT_ACTION;
        var incoming = edges.stream().anyMatch(edge -> edge.toNode == nodeId
                && edge.toPort == node.kind.flowInputPort());
        var outgoing = edges.stream().anyMatch(edge -> edge.fromNode == nodeId
                && edge.fromPort == node.kind.flowOutputPort());
        return new FlowPosition(true, incoming, outgoing);
    }

    public enum PortType {
        ENTITY,
        ENTITY_SET,
        DESTINATION,
        FLOW
    }

    public enum PortDirection {
        INPUT,
        OUTPUT
    }

    public enum NodeCategory {
        SOURCE,
        COLLECTION,
        FILTER,
        ACTION,
        CONTROL
    }

    public enum NodeGroup {
        TARGET,
        COLLECTION,
        FILTER,
        MENTAL_ACTION,
        CONTROL_ACTION
    }

    public enum ParameterKind {
        NONE,
        RANGE,
        COUNT,
        CAPABILITY,
        SORT_DIRECTION,
        ENTITY_TYPE,
        HEALTH_PERCENT,
        DURATION_SECONDS
    }

    public enum NodeKind {
        CASTER(0, NodeCategory.SOURCE, NodeGroup.TARGET, in(), out(entity("entity"))),
        ROSTER(1, NodeCategory.SOURCE, NodeGroup.TARGET, in(), out(set("entities"))),
        INTRUSION_TARGET(2, NodeCategory.SOURCE, NodeGroup.TARGET, in(), out(entity("entity"))),
        LOOK_TARGET(3, NodeCategory.SOURCE, NodeGroup.TARGET, in(), out(entity("entity"))),
        NEARBY_ENTITIES(4, NodeCategory.SOURCE, NodeGroup.TARGET, in(), out(set("entities")), ParameterKind.RANGE),

        ALIVE(5, NodeCategory.FILTER, NodeGroup.FILTER, in(set("entities")), out(set("entities"))),
        DISTANCE(6, NodeCategory.FILTER, NodeGroup.FILTER, in(set("entities")), out(set("entities")), ParameterKind.RANGE),
        ALLIES(7, NodeCategory.FILTER, NodeGroup.FILTER, in(set("entities")), out(set("entities"))),
        ENEMIES(8, NodeCategory.FILTER, NodeGroup.FILTER, in(set("entities")), out(set("entities"))),
        ABILITY_SUPPORTED(9, NodeCategory.FILTER, NodeGroup.FILTER, in(set("entities")), out(set("entities")), ParameterKind.CAPABILITY),
        EXCLUDE(10, NodeCategory.COLLECTION, NodeGroup.COLLECTION, in(set("entities"), entity("excluded")), out(set("entities"))),
        NEAREST(11, NodeCategory.COLLECTION, NodeGroup.COLLECTION, in(set("entities")), out(entity("entity"))),
        LIMIT(12, NodeCategory.COLLECTION, NodeGroup.COLLECTION, in(set("entities")), out(set("entities")), ParameterKind.COUNT),

        TARGET_MISIDENTIFICATION(13, NodeCategory.ACTION, NodeGroup.MENTAL_ACTION, in(set("subjects"), entity("target")), ParameterKind.DURATION_SECONDS),
        MENTAL_STUPOR(14, NodeCategory.ACTION, NodeGroup.MENTAL_ACTION, in(set("subjects")), ParameterKind.DURATION_SECONDS),
        IMPRESSION_MANIPULATION(15, NodeCategory.ACTION, NodeGroup.MENTAL_ACTION, in(set("subjects")), ParameterKind.DURATION_SECONDS),
        PERCEPTION_MASK(16, NodeCategory.ACTION, NodeGroup.MENTAL_ACTION, in(set("observers"), entity("hidden")), ParameterKind.DURATION_SECONDS),
        START_INTRUSION(17, NodeCategory.ACTION, NodeGroup.MENTAL_ACTION, in(entity("target")), ParameterKind.DURATION_SECONDS),
        END_INTRUSION(18, NodeCategory.ACTION, NodeGroup.MENTAL_ACTION, in(), ParameterKind.NONE),

        TARGETED_BY(19, NodeCategory.FILTER, NodeGroup.FILTER, in(set("entities"), entity("target")), out(set("entities"))),
        HOSTILE_TO(20, NodeCategory.FILTER, NodeGroup.FILTER, in(set("entities"), entity("target")), out(set("entities"))),
        LAST_DAMAGED_BY(21, NodeCategory.FILTER, NodeGroup.FILTER, in(set("entities"), entity("attacker")), out(set("entities"))),
        PLAYER_TARGET(22, NodeCategory.SOURCE, NodeGroup.TARGET, in(), out(entity("entity"))),
        SORT_BY_DISTANCE(23, NodeCategory.COLLECTION, NodeGroup.COLLECTION, in(set("entities")), out(set("entities")), ParameterKind.SORT_DIRECTION),
        RANDOM(24, NodeCategory.COLLECTION, NodeGroup.COLLECTION, in(set("entities")), out(entity("entity"))),
        TYPE_FILTER(25, NodeCategory.FILTER, NodeGroup.FILTER, in(set("entities")), out(set("entities")), ParameterKind.ENTITY_TYPE),
        HEALTH_FILTER(26, NodeCategory.FILTER, NodeGroup.FILTER, in(set("entities")), out(set("entities")), ParameterKind.HEALTH_PERCENT),
        HAS_TARGET(27, NodeCategory.FILTER, NodeGroup.FILTER, in(set("entities")), out(set("entities"))),
        AFFECTED(28, NodeCategory.FILTER, NodeGroup.FILTER, in(set("entities")), out(set("entities"))),
        PATH_TO(29, NodeCategory.CONTROL, NodeGroup.CONTROL_ACTION, in(set("subjects"), destination("destination")), ParameterKind.DURATION_SECONDS),
        VIEW_CONTROL(30, NodeCategory.CONTROL, NodeGroup.CONTROL_ACTION, in(set("subjects"), entity("target")), ParameterKind.DURATION_SECONDS),
        REMOVE_CONTROL(31, NodeCategory.CONTROL, NodeGroup.CONTROL_ACTION, in(set("subjects")), ParameterKind.NONE),

        CURRENT_TARGET(32, NodeCategory.SOURCE, NodeGroup.TARGET, in(entity("subject")), out(entity("entity"))),
        LAST_ATTACKER(33, NodeCategory.SOURCE, NodeGroup.TARGET, in(entity("subject")), out(entity("entity"))),
        ENTITY_TO_SET(34, NodeCategory.COLLECTION, NodeGroup.COLLECTION, in(entity("entity")), out(set("entities"))),
        UNION(35, NodeCategory.COLLECTION, NodeGroup.COLLECTION, in(set("left"), set("right")), out(set("entities"))),
        INTERSECTION(36, NodeCategory.COLLECTION, NodeGroup.COLLECTION, in(set("left"), set("right")), out(set("entities"))),
        SUBTRACT_SET(37, NodeCategory.COLLECTION, NodeGroup.COLLECTION, in(set("left"), set("right")), out(set("entities"))),
        FARTHEST(38, NodeCategory.COLLECTION, NodeGroup.COLLECTION, in(set("entities")), out(entity("entity"))),
        LOWEST_HEALTH(39, NodeCategory.COLLECTION, NodeGroup.COLLECTION, in(set("entities")), out(entity("entity"))),
        HIGHEST_HEALTH(40, NodeCategory.COLLECTION, NodeGroup.COLLECTION, in(set("entities")), out(entity("entity"))),
        HEALTH_BELOW(41, NodeCategory.FILTER, NodeGroup.FILTER, in(set("entities")), out(set("entities")), ParameterKind.HEALTH_PERCENT),
        VISIBLE_FROM(42, NodeCategory.FILTER, NodeGroup.FILTER, in(set("entities"), entity("observer")), out(set("entities"))),
        SIGHT_POSITION(43, NodeCategory.SOURCE, NodeGroup.TARGET,
                in(entity("observer")), out(destination("destination"))),
        GUARD_MODE(44, NodeCategory.CONTROL, NodeGroup.CONTROL_ACTION,
                in(set("subjects"), destination("destination")), ParameterKind.DURATION_SECONDS);

        private static final Map<Integer, NodeKind> BY_WIRE_ID = new HashMap<>();

        static {
            for (var kind : values()) {
                if (BY_WIRE_ID.put(kind.wireId, kind) != null) {
                    throw new IllegalStateException("Duplicate precision node wire id " + kind.wireId);
                }
            }
        }

        private final int wireId;
        private final NodeCategory category;
        private final NodeGroup group;
        private final List<PortDefinition> inputDefinitions;
        private final List<PortDefinition> outputDefinitions;
        private final List<PortType> inputs;
        private final List<PortType> outputs;
        private final ParameterKind parameterKind;

        NodeKind(
                int wireId,
                NodeCategory category,
                NodeGroup group,
                List<PortDefinition> inputs,
                List<PortDefinition> outputs
        ) {
            this(wireId, category, group, inputs, outputs, ParameterKind.NONE);
        }

        NodeKind(
                int wireId,
                NodeCategory category,
                NodeGroup group,
                List<PortDefinition> inputs,
                List<PortDefinition> outputs,
                ParameterKind parameterKind
        ) {
            this.wireId = wireId;
            this.category = category;
            this.group = group;
            this.parameterKind = parameterKind;
            this.inputDefinitions = definePorts(inputs, PortDirection.INPUT, true);
            this.outputDefinitions = definePorts(outputs, PortDirection.OUTPUT, false);
            this.inputs = this.inputDefinitions.stream().map(PortDefinition::type).toList();
            this.outputs = this.outputDefinitions.stream().map(PortDefinition::type).toList();
        }

        NodeKind(
                int wireId,
                NodeCategory category,
                NodeGroup group,
                List<PortDefinition> dataInputs,
                ParameterKind parameterKind
        ) {
            this.wireId = wireId;
            this.category = category;
            this.group = group;
            this.parameterKind = parameterKind;
            var actionInputs = new ArrayList<>(definePorts(dataInputs, PortDirection.INPUT, true));
            actionInputs.add(new PortDefinition("flow", PortType.FLOW, PortDirection.INPUT, false, 1));
            this.inputDefinitions = List.copyOf(actionInputs);
            this.outputDefinitions = List.of(new PortDefinition(
                    "flow", PortType.FLOW, PortDirection.OUTPUT, false, 1));
            this.inputs = this.inputDefinitions.stream().map(PortDefinition::type).toList();
            this.outputs = this.outputDefinitions.stream().map(PortDefinition::type).toList();
        }

        public static NodeKind byWireId(int wireId) {
            return BY_WIRE_ID.get(wireId);
        }

        private static List<PortDefinition> definePorts(
                List<PortDefinition> ports,
                PortDirection direction,
                boolean required
        ) {
            return ports.stream().map(port -> new PortDefinition(
                    port.key,
                    port.type,
                    direction,
                    required,
                    direction == PortDirection.INPUT ? 1 : Integer.MAX_VALUE
            )).toList();
        }

        private static List<PortDefinition> in(PortDefinition... ports) {
            return List.of(ports);
        }

        private static List<PortDefinition> out(PortDefinition... ports) {
            return List.of(ports);
        }

        private static PortDefinition entity(String key) {
            return new PortDefinition(key, PortType.ENTITY, PortDirection.INPUT, true, 1);
        }

        private static PortDefinition set(String key) {
            return new PortDefinition(key, PortType.ENTITY_SET, PortDirection.INPUT, true, 1);
        }

        private static PortDefinition destination(String key) {
            return new PortDefinition(key, PortType.DESTINATION, PortDirection.INPUT, true, 1);
        }

        public int wireId() {
            return wireId;
        }

        public NodeCategory category() {
            return category;
        }

        public NodeGroup group() {
            return group;
        }

        public List<PortDefinition> inputDefinitions() {
            return inputDefinitions;
        }

        public List<PortDefinition> outputDefinitions() {
            return outputDefinitions;
        }

        public List<PortType> inputs() {
            return inputs;
        }

        public List<PortType> outputs() {
            return outputs;
        }

        public ParameterKind parameterKind() {
            return parameterKind;
        }

        public boolean isAction() {
            return category == NodeCategory.ACTION || category == NodeCategory.CONTROL;
        }

        public int dataInputCount() {
            return isAction() ? inputDefinitions.size() - 1 : inputDefinitions.size();
        }

        public int flowInputPort() {
            return isAction() ? inputDefinitions.size() - 1 : -1;
        }

        public int flowOutputPort() {
            return isAction() ? 0 : -1;
        }

        public boolean isParameterValid(double value) {
            return switch (parameterKind) {
                case RANGE -> value >= 1.0 && value <= 32.0;
                case COUNT -> value >= 1.0 && value <= 8.0 && value == Math.rint(value);
                case CAPABILITY -> value >= 0.0 && value < ControlCapability.values().length
                        && value == Math.rint(value);
                case SORT_DIRECTION -> value >= 0.0 && value <= 1.0 && value == Math.rint(value);
                case ENTITY_TYPE -> value >= 0.0 && value <= 3.0 && value == Math.rint(value);
                case HEALTH_PERCENT -> value >= 1.0 && value <= 100.0 && value == Math.rint(value);
                case DURATION_SECONDS -> value == 0.0
                        || value >= 1.0 && value <= 3600.0 && value == Math.rint(value);
                case NONE -> value == 0.0;
            };
        }

        public double defaultParameter() {
            return switch (parameterKind) {
                case RANGE -> 16.0;
                case COUNT -> 4.0;
                case HEALTH_PERCENT -> 50.0;
                case DURATION_SECONDS -> 0.0;
                default -> 0.0;
            };
        }
    }

    public enum Diagnostic {
        OK,
        MALFORMED,
        TOO_LARGE,
        TOO_MANY_NODES,
        TOO_MANY_EDGES,
        INVALID_NODE,
        NON_FINITE_VALUE,
        DUPLICATE_NODE,
        INVALID_PARAMETER,
        NO_ACTION,
        DUPLICATE_EDGE,
        INVALID_EDGE,
        INVALID_PORT,
        TYPE_MISMATCH,
        MULTIPLE_INPUTS,
        MISSING_INPUT,
        INVALID_FLOW,
        BRANCHED_FLOW,
        DISCONNECTED_FLOW,
        CYCLE,
        REVISION_CONFLICT,
        EMPTY_PROGRAM,
        TARGET_LIMIT,
        PROTECTED_TARGET,
        UNSUPPORTED_TARGET,
        INSUFFICIENT_CP,
        ACTION_FAILED,
        SKILL_UNAVAILABLE,
        FLOW_CYCLE,
        NO_SIGHT_TARGET,
        NO_EFFECTIVE_SUBJECTS,
        UNREACHABLE_DESTINATION,
        TARGET_UNAVAILABLE,
        ADAPTER_ERROR;

        public String translationKey() {
            return "message.academy.precision_operation." + name().toLowerCase(Locale.ROOT);
        }
    }

    public record PortDefinition(
            String key,
            PortType type,
            PortDirection direction,
            boolean required,
            int maxConnections
    ) {
    }

    public record FlowPosition(boolean action, boolean hasIncoming, boolean hasOutgoing) {
        public static final FlowPosition NOT_ACTION = new FlowPosition(false, false, false);

        public boolean isOpenInput() {
            return action && !hasIncoming;
        }

        public boolean isOpenOutput() {
            return action && !hasOutgoing;
        }
    }

    public record Node(int id, NodeKind kind, double parameter, double x, double y) {
        public Node normalized() {
            return new Node(
                    id,
                    kind,
                    parameter,
                    Math.clamp(x, -100_000.0, 100_000.0),
                    Math.clamp(y, -100_000.0, 100_000.0)
            );
        }
    }

    public record Edge(int fromNode, int fromPort, int toNode, int toPort) {
    }

    public record Validation(
            boolean valid,
            Diagnostic diagnostic,
            int nodeId,
            int port,
            PrecisionGraph normalized,
            List<Integer> topologicalOrder,
            List<Integer> actionOrder
    ) {
        private static Validation error(Diagnostic diagnostic) {
            return error(diagnostic, -1, -1);
        }

        private static Validation error(Diagnostic diagnostic, int nodeId, int port) {
            return new Validation(false, diagnostic, nodeId, port, EMPTY, List.of(), List.of());
        }
    }

    public record Migration(PrecisionGraph graph, Diagnostic diagnostic) {
        public boolean valid() {
            return diagnostic == Diagnostic.OK;
        }
    }
}
