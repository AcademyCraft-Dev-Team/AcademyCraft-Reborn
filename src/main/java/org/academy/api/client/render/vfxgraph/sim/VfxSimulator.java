package org.academy.api.client.render.vfxgraph.sim;

import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.model.GraphParameter;
import org.academy.api.client.render.graph.type.Curve;
import org.academy.api.client.render.graph.type.Gradient;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodeRegistry;

import java.util.*;

/**
 * CPU 粒子模拟器。把有序节点序列（spawn → init → update → …）逐帧执行到共享 [ParticleBuffer]。
 *
 * <p>VFX 图是「有状态有序 passes」而非数据流，故按调用方给定的顺序执行节点。
 * 黑板参数（含 CURVE/GRADIENT，M13-05 over-life 引用）随模拟上下文提供给节点。
 * 存活参数（M15-04）：[setLiveParam] 不重建模拟器，驱动节点经 {@code param} 属性每帧读取。</p>
 */
public final class VfxSimulator {
    private final ParticleBuffer buffer = new ParticleBuffer();
    private final List<SimNode> nodes = new ArrayList<>();
    private final Map<String, Curve> curves = new LinkedHashMap<>();
    private final Map<String, Gradient> gradients = new LinkedHashMap<>();
    private final Map<String, Value> liveParams = new LinkedHashMap<>();
    private final Random random;
    private float time;

    public VfxSimulator(List<GraphNode> orderedNodes, VfxNodeRegistry registry) {
        this(orderedNodes, registry, 0L, List.of());
    }

    public VfxSimulator(List<GraphNode> orderedNodes, VfxNodeRegistry registry, long seed) {
        this(orderedNodes, registry, seed, List.of());
    }

    public VfxSimulator(List<GraphNode> orderedNodes, VfxNodeRegistry registry, long seed, List<GraphParameter> parameters) {
        this.random = new Random(seed);
        for (var p : parameters) {
            liveParams.put(p.id(), p.defaultValue());
            if (p.type() == ValueType.CURVE) {
                curves.put(p.id(), p.defaultValue().asCurve());
            } else if (p.type() == ValueType.GRADIENT) {
                gradients.put(p.id(), p.defaultValue().asGradient());
            }
        }
        for (var node : orderedNodes) {
            var factory = registry.find(node.type());
            if (factory == null) {
                throw new IllegalStateException("no VFX node factory for: " + node.type());
            }
            nodes.add(factory.create(node));
        }
    }

    /**
     * 注入存活参数（不重建模拟器，M15-04）。
     */
    public void setLiveParam(String parameterId, Value value) {
        liveParams.put(parameterId, value);
    }

    public void step(float dt) {
        var ctx = new SimContext(dt, time, random, curves, gradients, liveParams);
        for (var node : nodes) {
            node.step(buffer, ctx);
        }
        time += dt;
    }

    public ParticleBuffer buffer() {
        return buffer;
    }

    public float time() {
        return time;
    }

    /**
     * 设置累计时间（loop 重启时延续时间戳，避免 time 归零，M28b）。
     */
    public void setTime(float time) {
        this.time = time;
    }
}
