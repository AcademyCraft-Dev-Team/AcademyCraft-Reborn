package org.academy.api.client.render.vfxgraph;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.model.GraphParameter;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.arc.ArcBuffer;
import org.academy.api.client.render.vfxgraph.arc.EffectArcSource;
import org.academy.api.client.render.vfxgraph.block.BlockGeometryBuffer;
import org.academy.api.client.render.vfxgraph.block.BlockGeometryFrame;
import org.academy.api.client.render.vfxgraph.block.EffectBlockGeometrySource;
import org.academy.api.client.render.vfxgraph.instance.EffectInstanceGeometrySource;
import org.academy.api.client.render.vfxgraph.instance.InstanceGeometryBuffer;
import org.academy.api.client.render.vfxgraph.model.VfxSystem;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodeRegistry;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
import org.academy.api.client.render.vfxgraph.render.GraphCamera;
import org.academy.api.client.render.vfxgraph.render.RenderSpec;
import org.academy.api.client.render.vfxgraph.render.VfxGraphRenderer;
import org.academy.api.client.render.vfxgraph.render.WorldTransform;
import org.academy.api.client.render.vfxgraph.shape.SurfaceProjector;
import org.academy.api.client.render.vfxgraph.shape.SurfaceSampler;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.render.vfxgraph.sim.VfxSimulator;
import org.academy.api.client.render.vfxgraph.sim.VfxSystemSimulator;
import org.jspecify.annotations.Nullable;

import java.util.*;

public final class GraphEffect {
    private final @Nullable VfxNodeRegistry registry;
    private final List<GraphNode> sourceNodes;
    private final List<GraphParameter> parameters;
    private final Map<String, String> overrides = new HashMap<>();
    private final Map<String, Value> liveParams = new HashMap<>();
    private final List<RenderSpec> specs;
    private @Nullable VfxSimulator simulator;
    private @Nullable VfxSystemSimulator systemSimulator;
    private @Nullable VfxGraphRenderer renderer;
    private float simTimeSeconds;
    private @Nullable InstanceGeometryBuffer instanceGeometry;
    private @Nullable EffectInstanceGeometrySource instanceGeometrySource;
    private @Nullable BlockGeometryBuffer blockGeometry;
    private @Nullable EffectBlockGeometrySource blockGeometrySource;
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
            Objects.requireNonNull(simulator).setLiveParam(parameterId, value);
        }
    }

    /**
     * 当前存活参数值（渲染期读取，如 {@code flash_cap}）；未绑定返回 null。
     */
    public @Nullable Value liveParam(String parameterId) {
        return liveParams.get(parameterId);
    }

    /**
     * Supplies an emitter-local projection surface to container simulation blocks.
     */
    public void setSurfaceProjector(String name, SurfaceProjector surface) {
        if (systemSimulator != null) systemSimulator.setSurfaceProjector(name, surface);
    }

    public void setSurfaceSampler(String name, SurfaceSampler sampler) {
        if (systemSimulator != null) systemSimulator.setSurfaceSampler(name, sampler);
    }

    public void setArcSource(String name, EffectArcSource source) {
        if (systemSimulator != null) systemSimulator.setArcSource(name, source);
    }

    public void setInstanceGeometrySource(EffectInstanceGeometrySource source) {
        instanceGeometrySource = source;
        if (instanceGeometry == null) {
            instanceGeometry = new InstanceGeometryBuffer();
        }
    }

    public @Nullable InstanceGeometryBuffer instanceGeometry() {
        return instanceGeometry;
    }

    public void setBlockGeometrySource(EffectBlockGeometrySource source) {
        blockGeometrySource = source;
        if (blockGeometry == null) {
            blockGeometry = new BlockGeometryBuffer();
        }
    }

    public @Nullable BlockGeometryBuffer blockGeometry() {
        return blockGeometry;
    }

    public @Nullable BlockGeometryFrame blockGeometryFrame() {
        return blockGeometrySource != null ? blockGeometrySource.sample(simTimeSeconds) : null;
    }

    public void tick(float dt) {
        if (dirty) {
            rebuild();
        }
        if (systemSimulator != null) {
            systemSimulator.step(dt);
        } else {
            Objects.requireNonNull(simulator).step(dt);
        }
        simTimeSeconds += dt;
        if (instanceGeometry != null && instanceGeometrySource != null) {
            instanceGeometry.setFrame(instanceGeometrySource.sample(simTimeSeconds));
        }
        if (blockGeometry != null && blockGeometrySource != null) {
            blockGeometry.setFrame(blockGeometrySource.sample(simTimeSeconds));
        }
    }

    public ParticleBuffer buffer() {
        return systemSimulator != null ? systemSimulator.buffer() : Objects.requireNonNull(simulator).buffer();
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
    public void render(GpuTextureView target, @Nullable GpuTextureView depth, GraphCamera camera,
                       @Nullable VfxGraphRenderer sharedRenderer, boolean clear, WorldTransform transform) {
        render(target, depth, camera, sharedRenderer, clear, transform, false);
    }

    /**
     * 运行时渲染，可选择仅输出 glow/bloom 输入。
     *
     * @param bloomPass glow/bloom 输入（renderGlowFrame）：只画 GLOW 输出规格，translucent 层不参与 bloom
     */
    public void render(GpuTextureView target, @Nullable GpuTextureView depth, GraphCamera camera,
                       @Nullable VfxGraphRenderer sharedRenderer, boolean clear, WorldTransform transform, boolean bloomPass) {
        if (dirty) {
            rebuild();
        }
        var renderer = sharedRenderer != null ? sharedRenderer : this.renderer;
        if (renderer == null) {
            renderer = new VfxGraphRenderer();
            this.renderer = renderer;
        }
        var buffer = systemSimulator != null ? systemSimulator.buffer() : Objects.requireNonNull(simulator).buffer();
        renderer.setArcBuffer(arcBuffer());
        renderer.setInstanceGeometry(instanceGeometry);
        renderer.setBlockGeometry(blockGeometry);
        renderer.render(target, depth, buffer, camera, clear, specs, transform, bloomPass);
    }

    private void rebuild() {
        var nodes = new ArrayList<GraphNode>(sourceNodes.size());
        for (var node : sourceNodes) {
            nodes.add(applyOverrides(node));
        }
        simulator = new VfxSimulator(nodes, Objects.requireNonNull(registry), 0L, parameters);
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
