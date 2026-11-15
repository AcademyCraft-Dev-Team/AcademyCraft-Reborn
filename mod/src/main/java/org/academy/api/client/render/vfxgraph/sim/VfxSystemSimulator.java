package org.academy.api.client.render.vfxgraph.sim;

import org.academy.api.client.render.graph.model.Edge;
import org.academy.api.client.render.graph.model.GraphParameter;
import org.academy.api.client.render.graph.type.Curve;
import org.academy.api.client.render.graph.type.Gradient;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.curve.*;
import org.academy.api.client.render.vfxgraph.model.VfxContext;
import org.academy.api.client.render.vfxgraph.model.VfxContextType;
import org.academy.api.client.render.vfxgraph.model.VfxOperatorNode;
import org.academy.api.client.render.vfxgraph.model.VfxSystem;
import org.academy.api.client.render.vfxgraph.nodes.PortValueSource;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.operator.OperatorContext;
import org.academy.api.client.render.vfxgraph.operator.VfxOperator;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
import org.academy.api.client.render.vfxgraph.shape.SurfaceProjector;
import org.academy.api.client.render.vfxgraph.shape.SurfaceSampler;
import org.joml.Vector3f;

import java.util.*;

public final class VfxSystemSimulator {
    private final ParticleBuffer buffer = new ParticleBuffer();
    private final CurveBuffer arcBuffer = new CurveBuffer();
    private float arcDriftSpeed = 1.5f;
    private float arcNoiseStrength = 0.27f;
    private final Map<String, List<BlockNode>> spawnBlocks = new LinkedHashMap<>();
    private final Map<String, List<BlockNode>> initBlocks = new LinkedHashMap<>();
    private final Map<String, List<BlockNode>> updateBlocks = new LinkedHashMap<>();
    private final Map<String, List<String>> initUpstreamSpawns = new LinkedHashMap<>();
    private final Map<String, List<String>> initUpstreamSpawnBlocks = new LinkedHashMap<>();
    private boolean hasBlockFlows;
    private final List<String> spawnOrder = new ArrayList<>();
    private final List<String> initOrder = new ArrayList<>();
    private final List<String> updateOrder = new ArrayList<>();
    private final Map<String, Value> liveParams = new LinkedHashMap<>();
    private final Set<String> overriddenParams = new HashSet<>();
    private final Map<String, Curve> curves = new LinkedHashMap<>();
    private final Map<String, Gradient> gradients = new LinkedHashMap<>();
    private final Random random;
    private float time;
    private final Map<String, SurfaceProjector> surfaces = new HashMap<>();
    private final Map<String, EffectCurveSource> arcSources = new HashMap<>();

    private final Map<String, SurfaceSampler> samplers = new HashMap<>();

    public void setSurfaceSampler(String name, SurfaceSampler sampler) {
        samplers.put(name, Objects.requireNonNull(sampler));
    }

    public void setArcSource(String name, EffectCurveSource source) {
        arcSources.put(name, Objects.requireNonNull(source));
    }

    public void setSurfaceProjector(String name, SurfaceProjector surface) {
        surfaces.put(name, Objects.requireNonNull(surface));
    }

    private record BlockNode(String blockId, SimNode node) {
    }

    public VfxSystemSimulator(VfxSystem system, VfxBlockRegistry blockRegistry,
                              VfxOperatorRegistry operatorRegistry, long seed, List<GraphParameter> parameters) {
        this.random = new Random(seed);
        for (var p : parameters) {
            liveParams.put(p.id(), p.defaultValue());
            if (p.type() == ValueType.CURVE) {
                curves.put(p.id(), p.defaultValue().asCurve());
            } else if (p.type() == ValueType.GRADIENT) {
                gradients.put(p.id(), p.defaultValue().asGradient());
            }
        }
        buildPlan(system, blockRegistry, operatorRegistry);
    }

    public VfxSystemSimulator(VfxSystem system, VfxBlockRegistry blockRegistry, long seed, List<GraphParameter> parameters) {
        this(system, blockRegistry, new VfxOperatorRegistry(), seed, parameters);
    }

    public void setLiveParam(String parameterId, Value value) {
        liveParams.put(parameterId, value);
        overriddenParams.add(parameterId);
    }

    public void step(float dt) {
        var ctx = new SimContext(dt, time, random, curves, gradients, liveParams, overriddenParams, arcBuffer);
        ctx.setSurfaces(surfaces);
        ctx.setSamplers(samplers);
        ctx.setArcSources(arcSources);

        var spawnBatchesByBlock = new HashMap<String, List<SpawnBatch>>();
        var spawnBatchesByContext = new HashMap<String, List<SpawnBatch>>();
        for (var contextId : spawnOrder) {
            var ctxBatches = new ArrayList<SpawnBatch>();
            for (var bn : spawnBlocks.getOrDefault(contextId, List.of())) {
                bn.node().step(buffer, ctx);
                var blockBatches = new ArrayList<>(ctx.emittedBatches());
                ctx.clearEmittedBatches();
                spawnBatchesByBlock.put(bn.blockId(), blockBatches);
                ctxBatches.addAll(blockBatches);
            }
            spawnBatchesByContext.put(contextId, ctxBatches);
        }

        for (var contextId : initOrder) {
            for (var bn : initBlocks.getOrDefault(contextId, List.of())) {
                var upstreamBlocks = initUpstreamSpawnBlocks.getOrDefault(bn.blockId(), List.of());
                var incoming = new ArrayList<SpawnBatch>();
                if (!upstreamBlocks.isEmpty()) {
                    for (var spawnBlockId : upstreamBlocks) {
                        var batches = spawnBatchesByBlock.get(spawnBlockId);
                        if (batches != null) {
                            incoming.addAll(batches);
                        }
                    }
                } else if (!hasBlockFlows) {
                    for (var upstream : initUpstreamSpawns.getOrDefault(contextId, List.of())) {
                        var batches = spawnBatchesByContext.get(upstream);
                        if (batches != null) {
                            incoming.addAll(batches);
                        }
                    }
                }
                ctx.setIncomingBatches(incoming);
                bn.node().step(buffer, ctx);
            }
        }

        ctx.setIncomingBatches(List.of());
        for (var contextId : updateOrder) {
            for (var bn : updateBlocks.getOrDefault(contextId, List.of())) {
                bn.node().step(buffer, ctx);
            }
        }

        CurveSimulations.get().simulate(ctx, arcBuffer,
                new CurveSimulation.Config(arcDriftSpeed, arcNoiseStrength));
        arcBuffer.advance(dt, random);

        time += dt;
    }

    public ParticleBuffer buffer() {
        return buffer;
    }

    public CurveBuffer arcBuffer() {
        return arcBuffer;
    }

    public float time() {
        return time;
    }

    public void setTime(float time) {
        this.time = time;
    }

    private void buildPlan(VfxSystem system, VfxBlockRegistry blockRegistry, VfxOperatorRegistry operatorRegistry) {
        var contextsById = new LinkedHashMap<String, VfxContext>();
        for (var ctx : system.contexts()) {
            contextsById.put(ctx.id(), ctx);
        }

        var operators = new LinkedHashMap<String, VfxOperator>();
        var operatorInputs = new LinkedHashMap<String, Map<String, Edge.PortRef>>();
        var operatorById = new LinkedHashMap<String, VfxOperatorNode>();
        for (var node : system.operators()) {
            operatorById.put(node.id(), node);
        }
        var blockPortInputs = new LinkedHashMap<String, Map<String, Edge.PortRef>>();
        for (var edge : system.dataEdges()) {
            var to = edge.to();
            if (operatorById.containsKey(to.nodeId())) {
                operatorInputs.computeIfAbsent(to.nodeId(), _ -> new LinkedHashMap<>()).put(to.portId(), edge.from());
            } else {
                blockPortInputs.computeIfAbsent(to.nodeId(), _ -> new LinkedHashMap<>()).put(to.portId(), edge.from());
            }
        }

        var building = new HashSet<String>();
        for (var opId : operatorById.keySet()) {
            buildOperator(opId, operatorById, operatorInputs, operators, building, operatorRegistry);
        }

        var inDegree = new HashMap<String, Integer>();
        var adjacency = new HashMap<String, List<String>>();
        for (var id : contextsById.keySet()) {
            inDegree.put(id, 0);
            adjacency.put(id, new ArrayList<>());
        }
        for (var edge : system.flowEdges()) {
            var from = edge.fromContextId();
            var to = edge.toContextId();
            if (!adjacency.containsKey(from) || !adjacency.containsKey(to)) continue;
            adjacency.get(from).add(to);
            inDegree.merge(to, 1, Integer::sum);
        }
        var queue = new ArrayDeque<String>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }
        List<String> topoOrder = new ArrayList<>();
        while (!queue.isEmpty()) {
            var id = queue.poll();
            topoOrder.add(id);
            for (var next : adjacency.get(id)) {
                if (inDegree.merge(next, -1, Integer::sum) == 0) queue.add(next);
            }
        }

        for (var edge : system.flowEdges()) {
            var fromCtx = contextsById.get(edge.fromContextId());
            if (fromCtx != null && fromCtx.type() == VfxContextType.SPAWN) {
                initUpstreamSpawns.computeIfAbsent(edge.toContextId(), _ -> new ArrayList<>()).add(edge.fromContextId());
            }
        }

        for (var edge : system.blockFlows()) {
            hasBlockFlows = true;
            initUpstreamSpawnBlocks.computeIfAbsent(edge.toBlockId(), _ -> new ArrayList<>())
                    .add(edge.fromBlockId());
        }

        for (var contextId : topoOrder) {
            var ctx = contextsById.get(contextId);
            if (ctx == null) continue;
            var nodes = new ArrayList<BlockNode>();
            for (var block : ctx.blocks()) {
                var factory = blockRegistry.find(block.type());
                if (factory == null) {
                    throw new IllegalStateException("no VFX block factory for: " + block.type());
                }
                var portSource = blockPortSource(block.id(), blockPortInputs.getOrDefault(block.id(), Map.of()), operators);
                nodes.add(new BlockNode(block.id(), factory.create(block, portSource)));
            }
            switch (ctx.type()) {
                case SPAWN -> {
                    spawnBlocks.put(contextId, nodes);
                    spawnOrder.add(contextId);
                }
                case INITIALIZE -> {
                    initBlocks.put(contextId, nodes);
                    initOrder.add(contextId);
                }
                case UPDATE -> {
                    updateBlocks.put(contextId, nodes);
                    updateOrder.add(contextId);
                }
                case OUTPUT -> {
                    for (var block : ctx.blocks()) {
                        if ("vfx.block.output_arc".equals(block.type())) {
                            var props = block.properties();
                            arcDriftSpeed = floatProp(props, "drift_speed", arcDriftSpeed);
                            arcNoiseStrength = floatProp(props, "noise_strength", arcNoiseStrength);
                        }
                    }
                }
            }
        }
    }

    private void buildOperator(String opId, Map<String, VfxOperatorNode> operatorById,
                               Map<String, Map<String, Edge.PortRef>> operatorInputs,
                               Map<String, VfxOperator> out, Set<String> building,
                               VfxOperatorRegistry registry) {
        if (out.containsKey(opId)) return;
        if (!building.add(opId)) {
            throw new IllegalStateException("operator cycle detected involving: " + opId);
        }
        var node = operatorById.get(opId);
        var factory = registry.find(node.type());
        if (factory == null) {
            throw new IllegalStateException("no VFX operator factory for: " + node.type());
        }
        var inputs = new LinkedHashMap<String, VfxOperator>();
        for (var entry : operatorInputs.getOrDefault(opId, Map.of()).entrySet()) {
            var from = entry.getValue();
            var upstream = out.get(from.nodeId());
            if (upstream == null) {
                buildOperator(from.nodeId(), operatorById, operatorInputs, out, building, registry);
                upstream = out.get(from.nodeId());
            }
            inputs.put(entry.getKey(), upstream);
        }
        out.put(opId, factory.create(node, inputs));
        building.remove(opId);
    }

    private PortValueSource blockPortSource(String blockId, Map<String, Edge.PortRef> portInputs,
                                            Map<String, VfxOperator> operators) {
        if (portInputs.isEmpty()) {
            return PortValueSource.none();
        }
        var resolved = new LinkedHashMap<String, VfxOperator>();
        for (var entry : portInputs.entrySet()) {
            var op = operators.get(entry.getValue().nodeId());
            if (op != null) {
                resolved.put(entry.getKey(), op);
            }
        }
        if (resolved.isEmpty()) {
            return PortValueSource.none();
        }
        return (portId, particleIndex, buffer, ctx) -> {
            var op = resolved.get(portId);
            if (op == null) return null;
            return op.eval(new OperatorContext(buffer, particleIndex, ctx));
        };
    }

    private static float floatProp(Map<String, String> props, String key, float fallback) {
        var v = props.getOrDefault(key, "").trim();
        try {
            return v.isEmpty() ? fallback : Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
