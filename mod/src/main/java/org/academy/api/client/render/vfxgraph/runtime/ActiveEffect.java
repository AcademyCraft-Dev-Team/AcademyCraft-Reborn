package org.academy.api.client.render.vfxgraph.runtime;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.world.entity.Entity;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.GraphEffect;
import org.academy.api.client.render.vfxgraph.curve.EffectCurveSource;
import org.academy.api.client.render.vfxgraph.block.BlockGeometryFrame;
import org.academy.api.client.render.vfxgraph.block.EffectBlockGeometrySource;
import org.academy.api.client.render.vfxgraph.instance.EffectInstanceGeometrySource;
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
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class ActiveEffect {
    private final String assetKey;
    private final VfxNodeRegistry registry;
    private final @Nullable VfxBlockRegistry blockRegistry;
    private final @Nullable VfxOperatorRegistry operatorRegistry;
    private final Vector3f position = new Vector3f();
    private final Quaternionf rotation = new Quaternionf();
    private final Map<String, Supplier<Value>> bindings = new LinkedHashMap<>();
    private final Map<String, SurfaceProjector> surfaces = new LinkedHashMap<>();
    private final Map<String, EffectCurveSource> arcSources = new LinkedHashMap<>();
    private @Nullable EffectInstanceGeometrySource instanceGeometrySource;
    private @Nullable EffectBlockGeometrySource blockGeometrySource;

    public void bindArcs(String name, EffectCurveSource source) {
        arcSources.put(name, Objects.requireNonNull(source));
        effect.setArcSource(name, source);
    }

    public void bindInstanceGeometry(EffectInstanceGeometrySource source) {
        instanceGeometrySource = Objects.requireNonNull(source);
        effect.setInstanceGeometrySource(source);
    }

    public void bindBlockGeometry(EffectBlockGeometrySource source) {
        blockGeometrySource = Objects.requireNonNull(source);
        effect.setBlockGeometrySource(source);
    }

    public @Nullable BlockGeometryFrame blockGeometryFrame() {
        return effect.blockGeometryFrame();
    }

    private final Map<String, SurfaceSampler> samplers = new LinkedHashMap<>();

    public void bindSurfaceSampler(String name, SurfaceSampler sampler) {
        samplers.put(name, Objects.requireNonNull(sampler));
        effect.setSurfaceSampler(name, sampler);
    }

    private GraphEffect effect;
    private @Nullable EffectFrameBinding frameBinding;
    private float gameAgeSeconds;
    private float gameLifetimeSeconds = Float.POSITIVE_INFINITY;
    private String refreshKey = "";

    public String refreshKey() {
        return refreshKey;
    }

    public void refresh(String key) {
        refreshKey = Objects.requireNonNull(key);
        gameAgeSeconds = 0;
    }

    public void bindFrame(EffectFrameBinding binding) {
        frameBinding = Objects.requireNonNull(binding);
    }

    public void setTransform(Matrix4fc transform) {
        transform.getTranslation(position);
        transform.getUnnormalizedRotation(rotation).normalize();
        scale = transform.getScale(new Vector3f()).x;
    }

    public void setGameTimeLifetimeSeconds(float seconds) {
        gameLifetimeSeconds = Float.isFinite(seconds) && seconds > 0 ? seconds : Float.POSITIVE_INFINITY;
    }

    public float gameAgeSeconds() {
        return gameAgeSeconds;
    }

    public void bindGameTime(String parameter) {
        bind(parameter, () -> Value.of(gameAgeSeconds));
    }

    boolean updateFrame(float dt, @Nullable GraphCamera camera, float partialTick) {
        if (Float.isFinite(dt) && dt > 0) gameAgeSeconds += dt;
        if (updateState()) return true;
        if (followEntity != null && camera != null) position.set(followEntity.getPosition(partialTick).toVector3f());
        if (frameBinding != null && camera != null && !frameBinding.update(this, camera, partialTick)) {
            stop();
            return true;
        }
        return false;
    }

    public void bindSurface(String name, SurfaceProjector surface) {
        surfaces.put(name, Objects.requireNonNull(surface));
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

    public void setCullingSphere(Vector3f center, float radius) {
        if (!Float.isFinite(radius) || radius < 0 || !center.isFinite()) {
            throw new IllegalArgumentException("Invalid VFX bounds");
        }
        cullingCenter.set(center);
        cullingRadius = radius;
    }

    public Vector3f cullingCenter() {
        return cullingRadius >= 0 ? cullingCenter : position;
    }

    public float cullingRadius(float fallback) {
        return cullingRadius >= 0 ? cullingRadius : fallback;
    }

    public void setRenderDistance(float distance) {
        renderDistance = Math.max(0f, distance);
    }

    public float renderDistance(float fallback) {
        return renderDistance >= 0 ? renderDistance : fallback;
    }

    public boolean renderVisible() {
        return renderVisible;
    }

    void setRenderVisible(boolean visible) {
        renderVisible = visible;
    }

    float simulationStep(float dt, boolean visible) {
        hiddenElapsed += dt;
        if (!visible && hiddenElapsed < 0.2f) return 0f;
        var step = Math.min(hiddenElapsed, 0.25f);
        hiddenElapsed = 0f;
        return step;
    }

    private @Nullable Entity followEntity;
    private boolean stopped;

    ActiveEffect(String assetKey, Graph graph, VfxNodeRegistry registry, Vector3f position) {
        this.assetKey = assetKey;
        this.registry = registry;
        this.blockRegistry = null;
        this.operatorRegistry = null;
        this.position.set(position);
        this.effect = new GraphEffect(graph, registry);
    }

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

    public void setLifetimeSeconds(float lifetimeSeconds) {
        if (!Float.isFinite(lifetimeSeconds) || lifetimeSeconds <= 0f) {
            expiresAtNanos = Long.MAX_VALUE;
            return;
        }
        var durationNanos = (long) (lifetimeSeconds * 1_000_000_000L);
        expiresAtNanos = System.nanoTime() + Math.max(1L, durationNanos);
    }

    public void setAlwaysVisible(boolean alwaysVisible) {
        this.alwaysVisible = alwaysVisible;
    }

    public void setFrameVisible(boolean visible) {
        frameVisible = visible;
    }

    public boolean frameVisible() {
        return frameVisible;
    }

    public boolean isExpired() {
        return stopped || System.nanoTime() >= expiresAtNanos || gameAgeSeconds >= gameLifetimeSeconds;
    }

    public boolean alwaysVisible() {
        return alwaysVisible;
    }

    public void setMinimumFarPlane(float minimumFarPlane) {
        this.minimumFarPlane = Math.max(0f, minimumFarPlane);
    }

    public float minimumFarPlane() {
        return minimumFarPlane;
    }

    public void follow(@Nullable Entity entity) {
        this.followEntity = entity;
        if (entity != null) {
            this.position.set((float) entity.getX(), (float) entity.getY(), (float) entity.getZ());
        }
    }

    public void bind(String parameterId, Supplier<Value> supplier) {
        bindings.put(parameterId, supplier);
    }

    public void stop() {
        stopped = true;
    }

    public boolean isStopped() {
        return stopped;
    }

    public boolean tick(float dt) {
        if (updateFrame(dt, null, 0)) return true;
        simulate(dt);
        return false;
    }

    void simulate(float dt) {
        if (dt > 0f) effect.tick(dt);
    }

    boolean updateState() {
        if (isExpired()) {
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
            effect.setLiveParam(entry.getKey(), value);
        }
        surfaces.forEach(effect::setSurfaceProjector);
        samplers.forEach(effect::setSurfaceSampler);
        arcSources.forEach(effect::setArcSource);
        if (instanceGeometrySource != null) {
            effect.setInstanceGeometrySource(instanceGeometrySource);
        }
        if (blockGeometrySource != null) {
            effect.setBlockGeometrySource(blockGeometrySource);
        }
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

    void reload(Graph graph) {
        effect = new GraphEffect(graph, registry);
        surfaces.forEach(effect::setSurfaceProjector);
        samplers.forEach(effect::setSurfaceSampler);
        arcSources.forEach(effect::setArcSource);
        if (instanceGeometrySource != null) {
            effect.setInstanceGeometrySource(instanceGeometrySource);
        }
        if (blockGeometrySource != null) {
            effect.setBlockGeometrySource(blockGeometrySource);
        }
        for (var entry : bindings.entrySet()) {
            effect.setLiveParam(entry.getKey(), entry.getValue().get());
        }
    }

    void reload(VfxSystem system) {
        effect = GraphEffect.container(system, Objects.requireNonNull(blockRegistry),
                Objects.requireNonNull(operatorRegistry), system.parameters());
        surfaces.forEach(effect::setSurfaceProjector);
        samplers.forEach(effect::setSurfaceSampler);
        arcSources.forEach(effect::setArcSource);
        if (instanceGeometrySource != null) {
            effect.setInstanceGeometrySource(instanceGeometrySource);
        }
        if (blockGeometrySource != null) {
            effect.setBlockGeometrySource(blockGeometrySource);
        }
        for (var entry : bindings.entrySet()) {
            effect.setLiveParam(entry.getKey(), entry.getValue().get());
        }
    }
}
