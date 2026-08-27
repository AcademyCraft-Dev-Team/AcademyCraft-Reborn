package org.academy.api.client.render.vfxgraph;

import com.mojang.blaze3d.textures.GpuTextureView;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.model.GraphParameter;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.arc.ArcBuffer;
import org.academy.api.client.render.vfxgraph.model.VfxSystem;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodeRegistry;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
import org.academy.api.client.render.vfxgraph.render.GraphCamera;
import org.academy.api.client.render.vfxgraph.render.RenderSpec;
import org.academy.api.client.render.vfxgraph.render.VfxGraphRenderer;
import org.academy.api.client.render.vfxgraph.render.WorldTransform;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.render.vfxgraph.sim.VfxSimulator;
import org.academy.api.client.render.vfxgraph.sim.VfxSystemSimulator;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时 VFX 图效果（自持）：持有模拟器 + [VfxGraphRenderer]，
 * 支持按 (节点, 属性) 覆盖运行时参数，逐帧 tick 与渲染。
 *
 * <p>支持两种模型：扁平（{@link Graph} → [VfxSimulator]，M5–M7 路径）与
 * 容器（{@link VfxSystem} → [VfxSystemSimulator]，M23–M28 容器化路径，含批次 flow + 数据流算子）。
 * 容器构造经 {@link #container(VfxSystem, VfxBlockRegistry, VfxOperatorRegistry, List)}。</p>
 *
 * <p>注意：参数覆盖会在下次 tick 时重建模拟器，从而重置粒子状态（效果重启）。
 * 存活参数（[setLiveParam]，M15-04）不重建模拟器，供游戏值连续绑定。</p>
 */
public final class GraphEffect {
    private final VfxNodeRegistry registry;
    private final List<GraphNode> sourceNodes;
    private final List<GraphParameter> parameters;
    private final Map<String, String> overrides = new HashMap<>();
    private final Map<String, Value> liveParams = new HashMap<>();
    private final List<RenderSpec> specs;
    private VfxSimulator simulator;
    private VfxSystemSimulator systemSimulator;
    private VfxGraphRenderer renderer;
    private boolean dirty;

    public GraphEffect(Graph graph, VfxNodeRegistry registry) {
        this.registry = registry;
        this.sourceNodes = List.copyOf(graph.nodes());
        this.parameters = List.copyOf(graph.parameters());
        var outputSpecs = graph.nodes().stream()
                .filter(n -> n.type().startsWith("vfx.output_"))
                .map(RenderSpec::fromOutputNode)
                .toList();
        this.specs = outputSpecs.isEmpty() ? List.of(RenderSpec.DEFAULT) : outputSpecs;
        this.simulator = new VfxSimulator(sourceNodes, registry, 0L, parameters);
    }

    /**
     * 容器模型构造（M27）：VfxSystem → VfxSystemSimulator（批次 flow + 数据流算子）。
     */
    public static GraphEffect container(VfxSystem system, VfxBlockRegistry blockRegistry,
                                        VfxOperatorRegistry operatorRegistry, List<GraphParameter> parameters) {
        var effect = new GraphEffect(system, blockRegistry, operatorRegistry, parameters);
        return effect;
    }

    private GraphEffect(VfxSystem system, VfxBlockRegistry blockRegistry,
                        VfxOperatorRegistry operatorRegistry, List<GraphParameter> parameters) {
        this.registry = null;
        this.sourceNodes = List.of();
        this.parameters = List.copyOf(parameters);
        this.specs = systemSpecs(system);
        this.systemSimulator = new VfxSystemSimulator(system, blockRegistry, operatorRegistry, 0L, parameters);
    }

    /**
     * 容器系统全部输出块 → RenderSpec 列表（几何由 output 块类型派生，着色器/混合/层由图数据；M21n 多输出）。
     */
    private static List<RenderSpec> systemSpecs(VfxSystem system) {
        var out = new ArrayList<RenderSpec>();
        for (var context : system.contexts()) {
            for (var block : context.blocks()) {
                if (block.type().startsWith("vfx.block.output_")) {
                    out.add(RenderSpec.fromOutputNode(new GraphNode(
                            block.id(), block.type(), block.properties(), block.ports(), 0f, 0f)));
                }
            }
        }
        return out.isEmpty() ? List.of(RenderSpec.DEFAULT) : List.copyOf(out);
    }

    /**
     * 全部输出规格（M21n 多输出：分层外观由各 spec 的 layer 过滤表达）。
     */
    public List<RenderSpec> specs() {
        return specs;
    }

    /**
     * 主输出规格（首个，兼容单输出调用方）。
     */
    public RenderSpec spec() {
        return specs.getFirst();
    }

    /**
     * 覆盖某节点的某属性值（下次 tick 生效，重建模拟器）。
     */
    public void setParameter(String nodeId, String propertyId, String value) {
        overrides.put(nodeId + ':' + propertyId, value);
        dirty = true;
    }

    /**
     * 设置存活参数（不重建模拟器，M15-04）：游戏值经此绑定到图参数，
     * 驱动节点经 {@code param} 属性按参数 id 每帧读取。
     */
    public void setLiveParam(String parameterId, Value value) {
        liveParams.put(parameterId, value);
        if (systemSimulator != null) {
            systemSimulator.setLiveParam(parameterId, value);
        } else {
            simulator.setLiveParam(parameterId, value);
        }
    }

    public void tick(float dt) {
        if (dirty) {
            rebuild();
        }
        if (systemSimulator != null) {
            systemSimulator.step(dt);
        } else {
            simulator.step(dt);
        }
    }

    public ParticleBuffer buffer() {
        return systemSimulator != null ? systemSimulator.buffer() : simulator.buffer();
    }

    /**
     * 本帧活电弧缓冲（M22，ADR-026：路径驱动 spine，Tube 渲染）；扁平模型无电弧返回 null。
     */
    public @Nullable ArcBuffer arcBuffer() {
        return systemSimulator != null ? systemSimulator.arcBuffer() : null;
    }

    /**
     * 自持渲染（编辑器路径：私有渲染器 + 恒等变换 + 清屏）。
     */
    public void render(GpuTextureView target, @Nullable GpuTextureView depth, GraphCamera camera) {
        render(target, depth, camera, null, true, WorldTransform.identity(), false);
    }

    /**
     * 运行时渲染（M15-01/03）：复用共享渲染器、叠加世界变换、不清屏。
     *
     * @param sharedRenderer 由管理器按拓扑共享的渲染器；null 则用实例私有渲染器
     */
    public void render(GpuTextureView target, @org.jspecify.annotations.Nullable GpuTextureView depth, GraphCamera camera,
                       VfxGraphRenderer sharedRenderer, boolean clear, WorldTransform transform) {
        render(target, depth, camera, sharedRenderer, clear, transform, false);
    }

    /**
     * 运行时渲染，可选择仅输出 glow/bloom 输入。
     *
     * @param bloomPass glow/bloom 输入（renderGlowFrame）：只画 GLOW 输出规格，translucent 层不参与 bloom
     */
    public void render(GpuTextureView target, @org.jspecify.annotations.Nullable GpuTextureView depth, GraphCamera camera,
                       VfxGraphRenderer sharedRenderer, boolean clear, WorldTransform transform, boolean bloomPass) {
        if (dirty) {
            rebuild();
        }
        var renderer = sharedRenderer != null ? sharedRenderer : this.renderer;
        if (renderer == null) {
            renderer = new VfxGraphRenderer();
            this.renderer = renderer;
        }
        var buffer = systemSimulator != null ? systemSimulator.buffer() : simulator.buffer();
        renderer.setArcBuffer(arcBuffer());
        renderer.render(target, depth, buffer, camera, clear, specs, transform, bloomPass);
    }

    private void rebuild() {
        var nodes = new ArrayList<GraphNode>(sourceNodes.size());
        for (var node : sourceNodes) {
            nodes.add(applyOverrides(node));
        }
        simulator = new VfxSimulator(nodes, registry, 0L, parameters);
        for (var entry : liveParams.entrySet()) {
            simulator.setLiveParam(entry.getKey(), entry.getValue());
        }
        dirty = false;
    }

    private GraphNode applyOverrides(GraphNode node) {
        var any = false;
        var props = new HashMap<>(node.properties());
        for (var entry : overrides.entrySet()) {
            var colon = entry.getKey().indexOf(':');
            if (colon < 0) continue;
            if (entry.getKey().substring(0, colon).equals(node.id())) {
                props.put(entry.getKey().substring(colon + 1), entry.getValue());
                any = true;
            }
        }
        if (!any) {
            return node;
        }
        return new GraphNode(node.id(), node.type(), props, node.ports(), node.x(), node.y());
    }
}
