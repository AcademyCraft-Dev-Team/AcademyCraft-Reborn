package org.academy.api.client.render.vfxgraph.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.academy.api.client.render.graph.model.Edge;
import org.academy.api.client.render.graph.model.GraphParameter;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.arc.ArcBuffer;
import org.academy.api.client.render.vfxgraph.arc.ArcCurve;
import org.academy.api.client.render.vfxgraph.arc.CurveGenerator;
import org.academy.api.client.render.vfxgraph.arc.NoiseAnimator;
import org.academy.api.client.render.vfxgraph.arc.SurfaceConstraint;
import org.academy.api.client.render.vfxgraph.model.VfxContext;
import org.academy.api.client.render.vfxgraph.model.VfxContextType;
import org.academy.api.client.render.vfxgraph.model.VfxNode;
import org.academy.api.client.render.vfxgraph.model.VfxOperatorNode;
import org.academy.api.client.render.vfxgraph.model.VfxSystem;
import org.academy.api.client.render.vfxgraph.nodes.PortValueSource;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.operator.OperatorContext;
import org.academy.api.client.render.vfxgraph.operator.VfxOperator;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
/**
 * 容器 VFX 模拟器（M24–M25）：按 Context 阶段 + flow 边批次驱动共享 [ParticleBuffer]；
 * 数据边（算子→块）经 [VfxOperator] 求值驱动块输入端口。
 *
 * <p><b>批次语义</b>：spawn context 的块批量生成粒子并 {@code emitBatch} 记录本帧新粒子批次；
 * 执行器按 flow 边把上游 spawn context 的批次注入下游 init context（{@code SimContext.incomingBatches}）；
 * init 块只处理传入批次——**多 spawn 独立 init 互不干扰**，替代旧 {@code spawnStart} 单点耦合。</p>
 *
 * <p><b>阶段顺序</b>：SPAWN → INITIALIZE → UPDATE（各阶段内按 flow 拓扑序）；OUTPUT 不执行模拟
 * （仅提供 RenderSpec）。update 块处理全部存活粒子（不区分批次）。</p>
 *
 * <p><b>数据流（M25）</b>：构造时按 {@code system.dataEdges()} 构建算子求值 DAG（算子 id →
 * {@link VfxOperator}，含算子间输入连接）；每个块构建 {[blockId, portId] → VfxOperator} 端口值源，
 * 块内读取端口时求值（attr-read 逐粒子）。算子图环检测抛 {@link IllegalStateException}。</p>
 */
public final class VfxSystemSimulator {
    private final ParticleBuffer buffer = new ParticleBuffer();
    private final ArcBuffer arcBuffer = new ArcBuffer();
    private final SurfaceConstraint surfaceConstraint = new SurfaceConstraint();
    private float arcDriftSpeed = 1.5f;
    private float arcNoiseStrength = 0.27f;
    private float arcNoiseScale = 2.0f;
    private long arcNoiseSeed = 42L;
    private final Map<String, List<BlockNode>> spawnBlocks = new LinkedHashMap<>();
    private final Map<String, List<BlockNode>> initBlocks = new LinkedHashMap<>();
    private final Map<String, List<BlockNode>> updateBlocks = new LinkedHashMap<>();
    /** init context → 其上游（flow 直接相连的）SPAWN context id 列表。 */
    private final Map<String, List<String>> initUpstreamSpawns = new LinkedHashMap<>();
    /** init 块 id → 其块级 flow 上游 spawn 块 id 列表（M28b）。 */
    private final Map<String, List<String>> initUpstreamSpawnBlocks = new LinkedHashMap<>();
    /** 全图是否存在块级 flow：存在则进入「精确配对模式」（未配对 init 块收空批次），否则回退 context 级。 */
    private boolean hasBlockFlows;
    /** 各阶段的 context 执行顺序（flow 拓扑序，保持 DAG 内相对顺序）。 */
    private final List<String> spawnOrder = new ArrayList<>();
    private final List<String> initOrder = new ArrayList<>();
    private final List<String> updateOrder = new ArrayList<>();
    private final Map<String, Value> liveParams = new LinkedHashMap<>();
    private final Map<String, org.academy.api.client.render.graph.type.Curve> curves = new LinkedHashMap<>();
    private final Map<String, org.academy.api.client.render.graph.type.Gradient> gradients = new LinkedHashMap<>();
    private final Random random;
    private float time;

    /** 块执行节点：保留块 id 以便按块收集批次（块级 flow，M28b）。 */
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

    /** 无算子注册的缺省构造（M24 兼容：数据边恒无绑定）。 */
    public VfxSystemSimulator(VfxSystem system, VfxBlockRegistry blockRegistry, long seed, List<GraphParameter> parameters) {
        this(system, blockRegistry, new VfxOperatorRegistry(), seed, parameters);
    }

    /** 注入存活参数（不重建模拟器，M15-04）。 */
    public void setLiveParam(String parameterId, Value value) {
        liveParams.put(parameterId, value);
    }

    public void step(float dt) {
        var ctx = new SimContext(dt, time, random, curves, gradients, liveParams, arcBuffer);

        // Phase 1: SPAWN（按块收集本帧批次 + 按 context 汇总）
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

        // Phase 2: INITIALIZE（块级 flow 优先：该 init 块只处理其 blockFlow 上游 spawn 块的批次；
        //  无块级上游则回退到 context 级上游 SPAWN context 的批次）
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
                    // 无块级 flow 模式：回退 context 级（整上游 SPAWN context 批次）
                    for (var upstream : initUpstreamSpawns.getOrDefault(contextId, List.of())) {
                        var batches = spawnBatchesByContext.get(upstream);
                        if (batches != null) {
                            incoming.addAll(batches);
                        }
                    }
                }
                // 精确配对模式：无块级上游的 init 块收空批次（不误伤其它 spawn 粒子）
                ctx.setIncomingBatches(incoming);
                bn.node().step(buffer, ctx);
            }
        }

        // Phase 3: UPDATE（全部存活粒子；无传入批次）
        ctx.setIncomingBatches(List.of());
        for (var contextId : updateOrder) {
            for (var bn : updateBlocks.getOrDefault(contextId, List.of())) {
                bn.node().step(buffer, ctx);
            }
        }

        // 电弧：复刻 Blender 每帧从基线几何求值——弧拱重采样（Set Handle Positions + Resample，
        // 随 age 成长）+ 噪声位移 + 端点表面吸附 + 火花粒子速度/重力积分，然后老化。
        if (arcBuffer.count() > 0) {
            for (int i = 0; i < arcBuffer.count(); i++) {
                var arc = arcBuffer.arc(i);
                // 火花粒子：位置 += 速度×dt，速度 += 重力×dt（Blender Simulation Input.001 速度积分，
                // 重力 Combine XYZ(0,0,重力G)）。迷你管整体平移，方向保持速度方向。
                if (arc.sparkVelocity() != null) {
                    float[] v = arc.sparkVelocity();
                    float gx = 0f, gy = 0f, gz = -0.9f;
                    float vx = v[0] + gx * dt;
                    float vy = v[1] + gy * dt;
                    float vz = v[2] + gz * dt;
                    v[0] = vx;
                    v[1] = vy;
                    v[2] = vz;
                    float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                    float ux = len < 1e-6f ? 0f : vx / len;
                    float uy = len < 1e-6f ? 1f : vy / len;
                    float uz = len < 1e-6f ? 0f : vz / len;
                    // 迷你管当前半长（按原两点距离）
                    float half = arc.size() >= 2 ? 0.5f * dist(arc, 0, 1) : 0f;
                    float cx = (arc.x(0) + arc.x(arc.size() - 1)) * 0.5f;
                    float cy = (arc.y(0) + arc.y(arc.size() - 1)) * 0.5f;
                    float cz = (arc.z(0) + arc.z(arc.size() - 1)) * 0.5f;
                    cx += vx * dt;
                    cy += vy * dt;
                    cz += vz * dt;
                    arc.setPoint(0, cx - ux * half, cy - uy * half, cz - uz * half);
                    arc.setPoint(arc.size() - 1, cx + ux * half, cy + uy * half, cz + uz * half);
                    continue;
                }
                // Blender 每帧从 Curve Line 基线重新求值：Set Spline Type(Bezier) +
                // Set Handle Positions(控制柄随 age 成长) + Resample(12)。仅 Blender 弧（有 archBase）执行。
                if (arc.hasArchBase()) {
                    // 仿真区爬行（Blender 仿真区 Set Position）：弧基座沿切平面随机滑移累积，
                    // 端点随后被 SurfaceConstraint 吸附拉回表面（复刻「电弧群游走」观感）。
                    wanderArcBase(arc, random);
                    CurveGenerator.sampleSurfaceArch(arc);
                }
                float strength = arc.hasNoiseStrength() ? arc.noiseStrength() : arcNoiseStrength;
                if (strength > 1e-6f) {
                    // 每弧独立噪声种子（Blender 唯一ID 子组：Index×100+SceneTime×100 → 每弧/每帧稳定唯一 ID），
                    // 否则全部电弧共用同一噪声场 → 一起同向形变/同飘，观感"都长一个样"。
                    NoiseAnimator.animate(arc, time, arcDriftSpeed, strength, arcNoiseScale,
                            arcNoiseSeed + arc.seed() * 7919L);
                }
                if (arc.hasSurface()) {
                    surfaceConstraint.constrain(arc);
                }
            }
        }
        arcBuffer.advance(dt, random);

        time += dt;
    }

    public ParticleBuffer buffer() {
        return buffer;
    }

    public ArcBuffer arcBuffer() {
        return arcBuffer;
    }

    public float time() {
        return time;
    }

    /** 设置累计时间（loop 重启时延续时间戳，避免 time 归零导致 UI 冻结，M28b）。 */
    public void setTime(float time) {
        this.time = time;
    }

    // ---- 计划构建 ----

    private void buildPlan(VfxSystem system, VfxBlockRegistry blockRegistry, VfxOperatorRegistry operatorRegistry) {
        var contextsById = new LinkedHashMap<String, VfxContext>();
        for (var ctx : system.contexts()) {
            contextsById.put(ctx.id(), ctx);
        }

        // 数据边 → 算子求值 DAG
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

        // 递归构建算子（含算子间连接），环检测
        var building = new java.util.HashSet<String>();
        for (var opId : operatorById.keySet()) {
            buildOperator(opId, operatorById, operatorInputs, operators, building, operatorRegistry);
        }

        // flow 拓扑排序（Kahn）→ 每阶段内执行顺序
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
        var queue = new java.util.ArrayDeque<String>();
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

        // 上游 spawn 关系（init 直接上游的 SPAWN context）
        for (var edge : system.flowEdges()) {
            var fromCtx = contextsById.get(edge.fromContextId());
            if (fromCtx != null && fromCtx.type() == VfxContextType.SPAWN) {
                initUpstreamSpawns.computeIfAbsent(edge.toContextId(), _ -> new ArrayList<>()).add(edge.fromContextId());
            }
        }

        // 块级 flow（M28b）：init 块 → 其上游 spawn 块（精确批次配对）
        for (var edge : system.blockFlows()) {
            hasBlockFlows = true;
            initUpstreamSpawnBlocks.computeIfAbsent(edge.toBlockId(), _ -> new ArrayList<>())
                    .add(edge.fromBlockId());
        }

        // 按 context 建块（带端口值源）
        for (var contextId : topoOrder) {
            var ctx = contextsById.get(contextId);
            if (ctx == null) continue;
            var nodes = new ArrayList<BlockNode>();
            for (var block : ctx.blocks()) {
                var factory = blockRegistry.find(block.type());
                if (factory == null) {
                    throw new IllegalStateException("no VFX block factory for: " + block.type());
                }
                var portSource = blockPortSource(block.id(), blockPortInputs.get(block.id()), operators);
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
                    // 无模拟；但扫描 output_arc 块读取动画参数
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
                               Map<String, VfxOperator> out, java.util.Set<String> building,
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
        if (portInputs == null || portInputs.isEmpty()) {
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

    private static float dist(org.academy.api.client.render.vfxgraph.arc.ArcCurve arc, int a, int b) {
        float dx = arc.x(b) - arc.x(a);
        float dy = arc.y(b) - arc.y(a);
        float dz = arc.z(b) - arc.z(a);
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * M30 仿真区爬行（复刻 Blender 仿真区 {@code Set Position}）：
     * 弧基座沿表面切平面随机滑移，偏移累积到 {@link org.academy.api.client.render.vfxgraph.arc.ArcCurve#accumulateWander}。
     * 偏移 = {@code cross(normalize(Random[±1]³), 表面法线) × Random[0.01..0.03] × 游离速度}。
     * 端点随后被表面吸附拉回（复刻 Set Position.002），形成「电弧群游走/爬行」。
     */
    private void wanderArcBase(ArcCurve arc, Random random) {
        float nx = arc.archNx(), ny = arc.archNy(), nz = arc.archNz();
        float rl = 0f;
        float rx = 0f, ry = 0f, rz = 0f;
        for (int guard = 0; guard < 4; guard++) {
            rx = random.nextFloat() * 2f - 1f;
            ry = random.nextFloat() * 2f - 1f;
            rz = random.nextFloat() * 2f - 1f;
            rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (rl > 1e-6f) break;
        }
        if (rl < 1e-6f) return;
        rx /= rl;
        ry /= rl;
        rz /= rl;
        // cross(r, normal) → 切平面方向
        float cx = ry * nz - rz * ny;
        float cy = rz * nx - rx * nz;
        float cz = rx * ny - ry * nx;
        float cl = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (cl < 1e-6f) return;
        cx /= cl;
        cy /= cl;
        cz /= cl;
        // Random[0.01..0.03] × 游离速度
        float step = (0.01f + 0.02f * random.nextFloat())
                * (arc.hasDriftSpeed() ? arc.driftSpeed() : arcDriftSpeed);
        arc.accumulateWander(cx * step, cy * step, cz * step);
    }
}
