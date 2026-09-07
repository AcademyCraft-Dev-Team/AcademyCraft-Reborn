package org.academy.api.client.render.vfxgraph.runtime;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.world.entity.Entity;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.GraphEffect;
import org.academy.api.client.render.vfxgraph.model.VfxSystem;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodeRegistry;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
import org.academy.api.client.render.vfxgraph.render.GraphCamera;
import org.academy.api.client.render.vfxgraph.render.RenderSpec;
import org.academy.api.client.render.vfxgraph.render.VfxGraphRenderer;
import org.academy.api.client.render.vfxgraph.render.WorldTransform;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 运行时 VFX 图效果实例（M15-03）：包装 [GraphEffect] + 世界变换 + 实体跟随 + 参数绑定 + 生命周期。
 *
 * <p>粒子在发射器局部坐标模拟，渲染时叠加 [WorldTransform]（位置/朝向/缩放）映射到世界。
 * 绑定经 [bind] 注入存活参数（M15-04），逐 tick 采样 Supplier，不重建模拟器。
 * M27 支持容器模型构造（[VfxSystem] → [VfxSystemSimulator]）。</p>
 */
public final class ActiveEffect {
    private final String assetKey;
    private final VfxNodeRegistry registry;
    private final VfxBlockRegistry blockRegistry;
    private final VfxOperatorRegistry operatorRegistry;
    private final Vector3f position = new Vector3f();
    private final Quaternionf rotation = new Quaternionf();
    private final Map<String, Supplier<Value>> bindings = new LinkedHashMap<>();
    private final Map<String, org.academy.api.client.render.vfxgraph.shape.SurfaceProjector> surfaces = new LinkedHashMap<>();
    private GraphEffect effect;

    /** Bind a local projection surface; preserved through asset reloads, independent of player entities. */
    public void bindSurface(String name, org.academy.api.client.render.vfxgraph.shape.SurfaceProjector surface) {
        surfaces.put(name, java.util.Objects.requireNonNull(surface));
        effect.setSurfaceProjector(name, surface);
    }
    private float scale = 1f;
    private float minimumFarPlane;
    private long expiresAtNanos = Long.MAX_VALUE;
    private boolean alwaysVisible;
    private boolean frameVisible = true;
    private boolean renderVisible;
    private final Vector3f cullingCenter = new Vector3f();
    private float cullingRadius = -1f;
    private float renderDistance = -1f;
    private float hiddenElapsed;

    /** Explicit conservative world-space bounds, independent of the graph emitter origin. */
    public void setCullingSphere(Vector3f center, float radius) {
        if (!Float.isFinite(radius) || radius < 0 || !center.isFinite()) {
            throw new IllegalArgumentException("Invalid VFX bounds");
        }
        cullingCenter.set(center);
        cullingRadius = radius;
    }

    public Vector3f cullingCenter() { return cullingRadius >= 0 ? cullingCenter : position; }
    public float cullingRadius(float fallback) { return cullingRadius >= 0 ? cullingRadius : fallback; }
    public void setRenderDistance(float distance) { renderDistance = Math.max(0f, distance); }
    public float renderDistance(float fallback) { return renderDistance >= 0 ? renderDistance : fallback; }
    public boolean renderVisible() { return renderVisible; }
    void setRenderVisible(boolean visible) { renderVisible = visible; }

    /** Coarse offscreen simulation preserves ageing without per-frame particle/arc work. */
    float simulationStep(float dt, boolean visible) {
        hiddenElapsed += dt;
        if (!visible && hiddenElapsed < 0.2f) return 0f;
        float step = Math.min(hiddenElapsed, 0.25f);
        hiddenElapsed = 0f;
        return step;
    }
    private @org.jspecify.annotations.Nullable Entity followEntity;
    private boolean stopped;

    ActiveEffect(String assetKey, Graph graph, VfxNodeRegistry registry, Vector3f position) {
        this.assetKey = assetKey;
        this.registry = registry;
        this.blockRegistry = null;
        this.operatorRegistry = null;
        this.position.set(position);
        this.effect = new GraphEffect(graph, registry);
    }

    /**
     * 容器模型构造（M27）：VfxSystem → GraphEffect.container（批次 flow + 数据流算子）。
     */
    ActiveEffect(String assetKey, VfxSystem system, VfxNodeRegistry registry,
                 VfxBlockRegistry blockRegistry, VfxOperatorRegistry operatorRegistry, Vector3f position) {
        this.assetKey = assetKey;
        this.registry = registry;
        this.blockRegistry = blockRegistry;
        this.operatorRegistry = operatorRegistry;
        this.position.set(position);
        this.effect = GraphEffect.container(system, blockRegistry, operatorRegistry, system.parameters());
    }

    public String assetKey() {
        return assetKey;
    }

    public GraphEffect effect() {
        return effect;
    }

    public RenderSpec spec() {
        return effect.spec();
    }

    /**
     * 全部输出规格（M21n 多输出，供渲染器池/glow 判定）。
     */
    public List<RenderSpec> specs() {
        return effect.specs();
    }

    public Vector3f position() {
        return position;
    }

    public Quaternionf rotation() {
        return rotation;
    }

    public float scale() {
        return scale;
    }

    public void setPosition(Vector3f position) {
        this.position.set(position);
    }

    public void setRotation(Quaternionf rotation) {
        this.rotation.set(rotation);
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    /**
     * 为一次性效果设置真实时间寿命。使用单调时钟而不是累计模拟步长，避免管理器的
     * tick 与逐帧模拟同时推进时把寿命重复扣除。
     */
    public void setLifetimeSeconds(float lifetimeSeconds) {
        if (!Float.isFinite(lifetimeSeconds) || lifetimeSeconds <= 0f) {
            expiresAtNanos = Long.MAX_VALUE;
            return;
        }
        var durationNanos = (long) (lifetimeSeconds * 1_000_000_000L);
        expiresAtNanos = System.nanoTime() + Math.max(1L, durationNanos);
    }

    /**
     * 跳过管理器的距离与视锥剔除。适用于范围远大于发射器包围球、或本身输出屏幕空间画面的效果。
     */
    public void setAlwaysVisible(boolean alwaysVisible) {
        this.alwaysVisible = alwaysVisible;
    }

    /** External analytic effects may cull before simulation without freezing their presentation clock. */
    public void setFrameVisible(boolean visible) { frameVisible = visible; }
    public boolean frameVisible() { return frameVisible; }
    public boolean isExpired() { return stopped || System.nanoTime() >= expiresAtNanos; }

    public boolean alwaysVisible() {
        return alwaysVisible;
    }

    /**
     * 为超大世界空间效果提供独立的最小远裁剪面，不受客户端区块视距降低影响。
     */
    public void setMinimumFarPlane(float minimumFarPlane) {
        this.minimumFarPlane = Math.max(0f, minimumFarPlane);
    }

    public float minimumFarPlane() { return minimumFarPlane; }

    GraphCamera cameraForRendering(GraphCamera camera) {
        return minimumFarPlane > 0f ? camera.withMinimumFarPlane(minimumFarPlane) : camera;
    }

    /** 跟随实体：每 tick 把发射器原点对齐实体位置。 */
    public void follow(Entity entity) {
        this.followEntity = entity;
        if (entity != null) {
            this.position.set((float) entity.getX(), (float) entity.getY(), (float) entity.getZ());
        }
    }

    /**
     * 绑定存活参数：每次 tick 采样 supplier 写入图参数（不重建模拟器，M15-04）。
     */
    public void bind(String parameterId, Supplier<Value> supplier) {
        bindings.put(parameterId, supplier);
    }

    public void stop() {
        stopped = true;
    }

    public boolean isStopped() {
        return stopped;
    }

    /**
     * tick 并返回是否应移除（显式 stop 或跟随实体已移除）。
     */
    public boolean tick(float dt) {
        if (updateState()) return true;
        simulate(dt);
        return false;
    }

    void simulate(float dt) {
        if (dt > 0f) effect.tick(dt);
    }

    /** Lifecycle and bindings must run even when simulation or drawing is budgeted out. */
    boolean updateState() {
        if (stopped || System.nanoTime() >= expiresAtNanos) {
            stopped = true;
            return true;
        }
        if (followEntity != null) {
            if (followEntity.isRemoved()) {
                stopped = true;
                return true;
            }
            position.set((float) followEntity.getX(), (float) followEntity.getY(), (float) followEntity.getZ());
        }
        for (var entry : bindings.entrySet()) {
            var value = entry.getValue().get();
            if (value != null) {
                effect.setLiveParam(entry.getKey(), value);
            }
        }
        surfaces.forEach(effect::setSurfaceProjector);
        return false;
    }

    public WorldTransform worldTransform() {
        return new WorldTransform(position, rotation, scale);
    }

    public void render(GpuTextureView target, @Nullable GpuTextureView depth,
                       GraphCamera camera, VfxGraphRenderer renderer, boolean clear) {
        render(target, depth, camera, renderer, clear, false);
    }

    public void render(GpuTextureView target, @Nullable GpuTextureView depth,
                       GraphCamera camera, VfxGraphRenderer renderer, boolean clear, boolean bloomPass) {
        effect.render(target, depth, camera, renderer, clear, worldTransform(), bloomPass);
    }

    /**
     * 资产重载（M15-05）：用新解码的图重建效果，保留位置/绑定。
     */
    void reload(Graph graph) {
        effect = new GraphEffect(graph, registry);
        for (var entry : bindings.entrySet()) {
            var value = entry.getValue().get();
            if (value != null) {
                effect.setLiveParam(entry.getKey(), value);
            }
        }
    }

    /**
     * 容器资产重载（M27）：用新解码的 VfxSystem 重建效果，保留位置/绑定。
     */
    void reload(VfxSystem system) {
        effect = GraphEffect.container(system, blockRegistry, operatorRegistry, system.parameters());
        for (var entry : bindings.entrySet()) {
            var value = entry.getValue().get();
            if (value != null) {
                effect.setLiveParam(entry.getKey(), value);
            }
        }
    }
}
