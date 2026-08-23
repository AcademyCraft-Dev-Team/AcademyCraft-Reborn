package org.academy.api.client.render.vfxgraph.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.academy.api.client.render.graph.type.Curve;
import org.academy.api.client.render.graph.type.Gradient;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.arc.ArcBuffer;

/**
 * 模拟帧上下文：dt、累计时间、随机源、黑板曲线/渐变、存活参数、本帧 spawn 批次、电弧缓冲。
 */
public final class SimContext {
    private final float dt;
    private final float time;
    private final Random random;
    private final Map<String, Curve> curves;
    private final Map<String, Gradient> gradients;
    private final Map<String, Value> liveParams;
    private final ArcBuffer arcs;
    public int spawnStart;
    private final List<SpawnBatch> emittedBatches = new ArrayList<>();
    private List<SpawnBatch> incomingBatches = List.of();

    public SimContext(float dt, float time, Random random) {
        this(dt, time, random, Map.of(), Map.of(), Map.of());
    }

    public SimContext(float dt, float time, Random random, Map<String, Curve> curves, Map<String, Gradient> gradients) {
        this(dt, time, random, curves, gradients, Map.of());
    }

    public SimContext(float dt, float time, Random random, Map<String, Curve> curves, Map<String, Gradient> gradients,
                      Map<String, Value> liveParams) {
        this(dt, time, random, curves, gradients, liveParams, new ArcBuffer());
    }

    public SimContext(float dt, float time, Random random, Map<String, Curve> curves, Map<String, Gradient> gradients,
                      Map<String, Value> liveParams, ArcBuffer arcs) {
        this.dt = dt;
        this.time = time;
        this.random = random;
        this.curves = curves;
        this.gradients = gradients;
        this.liveParams = liveParams;
        this.arcs = arcs;
        this.spawnStart = 0;
    }

    public float dt() {
        return dt;
    }

    public float time() {
        return time;
    }

    public Random random() {
        return random;
    }

    public ArcBuffer arcs() {
        return arcs;
    }

    public void emitBatch(int start, int end) {
        emittedBatches.add(new SpawnBatch(start, end));
    }

    public List<SpawnBatch> emittedBatches() {
        return List.copyOf(emittedBatches);
    }

    public void clearEmittedBatches() {
        emittedBatches.clear();
    }

    public void setIncomingBatches(List<SpawnBatch> batches) {
        this.incomingBatches = List.copyOf(batches);
    }

    public List<SpawnBatch> incomingBatches() {
        return incomingBatches;
    }

    public boolean hasIncomingBatches() {
        return !incomingBatches.isEmpty();
    }

    public Map<String, Curve> curves() {
        return curves;
    }

    public Map<String, Gradient> gradients() {
        return gradients;
    }

    public Map<String, Value> liveParams() {
        return liveParams;
    }

    /** 遍历本帧新粒子索引（由 incomingBatches 汇总）。 */
    public void forEachIncoming(java.util.function.IntConsumer action) {
        for (var batch : incomingBatches) {
            for (int i = batch.start(); i < batch.end(); i++) {
                action.accept(i);
            }
        }
    }

    /** 读取存活浮点参数（有绑定返回绑定值，无则返回默认）。 */
    public float paramFloat(String paramId, float defaultVal) {
        if (paramId.isEmpty()) return defaultVal;
        var v = liveParams.get(paramId);
        return v != null && v.type() == org.academy.api.client.render.graph.type.ValueType.FLOAT ? v.asFloat() : defaultVal;
    }

    /** 读取存活 Vec3 参数的单个分量（index 0/1/2 = x/y/z）。 */
    public float paramVec3(String paramId, int index, float defaultVal) {
        if (paramId.isEmpty()) return defaultVal;
        var v = liveParams.get(paramId);
        if (v != null && v.type() == org.academy.api.client.render.graph.type.ValueType.VEC3) {
            var a = v.asVec3();
            return index == 0 ? a.x() : index == 1 ? a.y() : a.z();
        }
        return defaultVal;
    }

    /** 读取存活颜色参数的单个分量（index 0/1/2/3 = r/g/b/a）。 */
    public float paramColor(String paramId, int index, float defaultVal) {
        if (paramId.isEmpty()) return defaultVal;
        var v = liveParams.get(paramId);
        if (v != null && v.type() == org.academy.api.client.render.graph.type.ValueType.COLOR) {
            var a = v.asColor();
            return index == 0 ? a.x() : index == 1 ? a.y() : index == 2 ? a.z() : a.w();
        }
        return defaultVal;
    }

    /** 获取黑板曲线。 */
    public org.academy.api.client.render.graph.type.Curve curve(String id) {
        return curves.get(id);
    }

    /** 获取黑板渐变。 */
    public org.academy.api.client.render.graph.type.Gradient gradient(String id) {
        return gradients.get(id);
    }

    /** 获取存活参数值。 */
    public org.academy.api.client.render.graph.type.Value param(String id) {
        return liveParams.get(id);
    }

    /** 设置存活参数（如果不存在）。 */
    public void paramIfAbsent(String id, org.academy.api.client.render.graph.type.Value value) {
        liveParams.putIfAbsent(id, value);
    }

    /** 设置黑板曲线（如果不存在）。 */
    public void curveIfAbsent(String id, org.academy.api.client.render.graph.type.Curve curve) {
        curves.putIfAbsent(id, curve);
    }

    /** 设置黑板渐变（如果不存在）。 */
    public void gradientIfAbsent(String id, org.academy.api.client.render.graph.type.Gradient gradient) {
        gradients.putIfAbsent(id, gradient);
    }
}
