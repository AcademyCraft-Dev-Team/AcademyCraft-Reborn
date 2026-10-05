package org.academy.api.client.render.vfxgraph.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.academy.AcademyCraft;
import org.academy.api.client.compatibility.IrisIntegration;
import org.academy.api.client.render.graph.assets.GraphAssets;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.serialize.JsonGraphCodec;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.block.BlockGeometryFrame;
import org.academy.api.client.render.vfxgraph.model.VfxSystem;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodeRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodes;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
import org.academy.api.client.render.vfxgraph.operator.VfxOperators;
import org.academy.api.client.render.vfxgraph.render.GraphCamera;
import org.academy.api.client.render.vfxgraph.render.RenderSpec;
import org.academy.api.client.render.vfxgraph.render.VfxGraphRenderer;
import org.academy.api.client.render.vfxgraph.serialize.JsonVfxGraphCodec;
import org.academy.api.client.render.vfxgraph.serialize.VfxGraphSchemaVersion;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class VfxGraphManager {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    private static final int BLOCK_VERTEX_STRIDE = (3 + 2 + 4) * Float.BYTES + 2 * Short.BYTES + 4 * Byte.BYTES;
    public static final VfxGraphManager INSTANCE = new VfxGraphManager();

    private final SimpleNodeRegistry metadata = new SimpleNodeRegistry();
    private final VfxNodeRegistry vfxRegistry = new VfxNodeRegistry();
    private final VfxBlockRegistry blockRegistry = new VfxBlockRegistry();
    private final VfxOperatorRegistry operatorRegistry = new VfxOperatorRegistry();
    private final GraphAssets assets;
    private final Map<String, VfxSystem> containerAssets = new LinkedHashMap<>();
    private final EffectBudget budget = new EffectBudget();
    private final Set<ActiveEffect> effects = new LinkedHashSet<>();
    private final Map<List<RenderSpec>, VfxGraphRenderer> rendererPool = new LinkedHashMap<>();
    private @Nullable GraphFileWatcher fileWatcher;
    private boolean initialized;
    private long lastRenderNanos = -1;
    private @Nullable GraphCamera lastCamera;

    private VfxGraphManager() {
        VfxNodes.registerAll(metadata, vfxRegistry);
        VfxBlocks.registerAll(metadata, blockRegistry);
        VfxOperators.registerAll(metadata, operatorRegistry);
        assets = new GraphAssets(new JsonGraphCodec(metadata));
    }

    public GraphAssets assets() {
        return assets;
    }

    public VfxNodeRegistry vfxRegistry() {
        return vfxRegistry;
    }

    public EffectBudget budget() {
        return budget;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void init() {
        initialized = true;
    }

    public void close() {
        stopFileWatcher();
        effects.clear();
        for (var renderer : rendererPool.values()) {
            renderer.close();
        }
        rendererPool.clear();
        initialized = false;
    }

    public void startFileWatcher(Path root) {
        if (fileWatcher != null) {
            return;
        }
        try {
            var watcher = new GraphFileWatcher(root);
            watcher.registerTree();
            watcher.startLoop();
            fileWatcher = watcher;
        } catch (IOException exception) {
            LOGGER.warn("Unable to start vfx graph file watcher at {}", root, exception);
        }
    }

    public void stopFileWatcher() {
        if (fileWatcher != null) {
            fileWatcher.close();
            fileWatcher = null;
        }
    }

    public void tick(float dt) {
        if (!initialized) return;
        var iterator = effects.iterator();
        while (iterator.hasNext()) {
            var effect = iterator.next();
            if (effect.updateFrame(dt, lastCamera, 0)) {
                iterator.remove();
                continue;
            }
            if (budget.canSpawnMore(effect.effect().buffer().count())) effect.simulate(dt);
        }
    }

    public record FrameStatistics(int active, int visible, int culled, int simulated) {
    }

    private FrameStatistics frameStatistics = new FrameStatistics(0, 0, 0, 0);
    private final Map<Float, GraphCamera> frameCameras = new HashMap<>();

    public FrameStatistics frameStatistics() {
        return frameStatistics;
    }

    public void renderFrame(GpuTextureView target, @Nullable GpuTextureView depth, GraphCamera camera) {
        if (!initialized) return;
        lastCamera = camera;
        frameCameras.clear();
        boolean paused = Minecraft.getInstance().isPaused();
        long now = System.nanoTime();
        float elapsed = paused ? 0f : lastRenderNanos <= 0 ? 1f / 60f
                : (now - lastRenderNanos) / 1e9f;
        float dt = Math.min(elapsed, 0.1f);
        lastRenderNanos = now;
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        int visible = 0, culled = 0, simulated = 0;
        var iterator = effects.iterator();
        while (iterator.hasNext()) {
            var effect = iterator.next();
            effect.setRenderVisible(false);
            if (effect.updateFrame(elapsed, camera, partialTick)) {
                iterator.remove();
                continue;
            }
            var effectCamera = frameCameras.computeIfAbsent(effect.minimumFarPlane(),
                    camera::withMinimumFarPlane);
            float radius = effect.cullingRadius(budget.effectRadius());
            boolean draw = effect.frameVisible() && (effect.alwaysVisible()
                    || (budget.shouldRender(effectCamera.position(), effect.cullingCenter(), radius,
                    effect.renderDistance(budget.maxRenderDistance()))
                    && budget.sphereInFrustum(effectCamera.projection(), effectCamera.viewRotation(),
                    effectCamera.position(), effect.cullingCenter(), radius)));
            effect.setRenderVisible(draw);
            // Analytic callers using frameVisible manage their own presentation clock.
            if (!paused && effect.frameVisible() && budget.canSpawnMore(effect.effect().buffer().count())) {
                float step = effect.simulationStep(dt, draw);
                if (step > 0f) {
                    effect.simulate(step);
                    simulated++;
                }
            }
            if (!draw) {
                culled++;
                continue;
            }
            visible++;
            var renderer = rendererPool.computeIfAbsent(effect.specs(), _ -> new VfxGraphRenderer());
            effect.render(target, depth, effectCamera, renderer, false, false);
        }
        frameStatistics = new FrameStatistics(effects.size(), visible, culled, simulated);
    }

    public void renderGlowFrame(GpuTextureView color, @Nullable GpuTextureView depth) {
        if (!initialized || lastCamera == null) return;
        for (var effect : effects) {
            if (!effect.renderVisible() || effect.isExpired() || !feedsBloom(effect)) continue;
            var camera = frameCameras.get(effect.minimumFarPlane());
            if (camera == null) continue;
            var renderer = rendererPool.computeIfAbsent(effect.specs(), _ -> new VfxGraphRenderer());
            effect.render(color, depth, camera, renderer, false, true);
        }
    }

    public void renderDistortionFrame(GpuTextureView target) {
        if (!initialized || lastCamera == null) return;
        for (var effect : effects) {
            if (!effect.renderVisible() || effect.isExpired()) continue;
            var camera = frameCameras.get(effect.minimumFarPlane());
            if (camera == null) continue;
            var specs = effect.specs();
            if (specs.stream().noneMatch(s -> s.geometry() == RenderSpec.Geometry.DISTORTION_RING)) continue;
            var renderer = rendererPool.computeIfAbsent(specs, _ -> new VfxGraphRenderer());
            var buffer = effect.effect().buffer();
            var transform = effect.worldTransform();
            for (var spec : specs) {
                if (spec.geometry() == RenderSpec.Geometry.DISTORTION_RING) {
                    renderer.renderDistortionRings(target, buffer, spec, effectDistortionRing(effect, spec), transform, camera);
                }
            }
        }
    }

    private static RenderSpec.DistortionRing effectDistortionRing(ActiveEffect effect, RenderSpec spec) {
        var d = spec.distortionRing();
        if (d == null) d = RenderSpec.DistortionRing.DEFAULT;
        var bound = effect.effect().liveParam("distortion_strength");
        float strength = d.strength();
        if (bound != null && bound.type() == ValueType.FLOAT) strength = Math.max(0f, bound.asFloat());
        bound = effect.effect().liveParam("distortion_ring_width");
        float width = d.width();
        if (bound != null && bound.type() == ValueType.FLOAT) width = Math.clamp(bound.asFloat(), 0.001f, 1f);
        bound = effect.effect().liveParam("distortion_edge_blur");
        float blur = d.edgeBlur();
        if (bound != null && bound.type() == ValueType.FLOAT) blur = Math.clamp(bound.asFloat(), 0f, 1f);
        return new RenderSpec.DistortionRing(strength, width, blur);
    }

    /**
     * 屏幕闪光帧（E1）：跨效果聚合所有 SCREEN_FLASH 输出的粒子 alpha，
     * 求和截断后由共享渲染器画单个全屏 quad（等同旧 SCREEN_SPACE_POST 求和语义）。
     */
    public void renderScreenFlashFrame(GpuTextureView target) {
        if (!initialized) return;
        List<VfxGraphRenderer.ScreenFlashSource> sources = null;
        VfxGraphRenderer renderer = null;
        for (var effect : effects) {
            if (!effect.renderVisible() || effect.isExpired()) continue;
            var specs = effect.specs();
            if (specs.stream().noneMatch(s -> s.geometry() == RenderSpec.Geometry.SCREEN_FLASH)) continue;
            if (sources == null) {
                sources = new ArrayList<>();
                renderer = rendererPool.computeIfAbsent(specs, _ -> new VfxGraphRenderer());
            }
            sources.add(new VfxGraphRenderer.ScreenFlashSource(
                    effect.effect().buffer(), specs, effectFlashCap(effect, specs)));
        }
        if (renderer != null && sources != null) {
            renderer.renderScreenFlash(target, sources);
        }
    }

    private static float effectFlashCap(ActiveEffect effect, List<RenderSpec> specs) {
        var bound = effect.effect().liveParam("flash_cap");
        if (bound != null && bound.type() == ValueType.FLOAT) return Math.max(0f, bound.asFloat());
        return specs.stream()
                .filter(s -> s.geometry() == RenderSpec.Geometry.SCREEN_FLASH)
                .map(RenderSpec::flashCap)
                .max(Float::compareTo)
                .orElse(1f);
    }

    private static boolean feedsBloom(ActiveEffect effect) {
        for (var spec : effect.specs()) if (spec.feedsBloom()) return true;
        return false;
    }

    public boolean hasGlowData() {
        if (!initialized) return false;
        for (var effect : effects) {
            if (effect.renderVisible() && !effect.isExpired() && feedsBloom(effect)) return true;
        }
        return false;
    }

    public void submitWorldGeometry(PoseStack poseStack, SubmitNodeCollector output, Vector3f cameraPos) {
        if (!initialized || !IrisIntegration.isShaderPackInUse()) return;
        for (var effect : effects) {
            if (effect.isExpired()) continue;
            if (effect.specs().stream().noneMatch(s -> s.geometry() == RenderSpec.Geometry.BLOCK_QUADS)) continue;
            var frame = effect.blockGeometryFrame();
            if (frame == null || frame.isEmpty()) continue;
            submitBlockGeometry(poseStack, output, cameraPos, frame);
        }
    }

    private static void submitBlockGeometry(PoseStack poseStack, SubmitNodeCollector output,
                                             Vector3f cameraPos, BlockGeometryFrame frame) {
        var worldPose = new PoseStack();
        worldPose.last().set(poseStack.last());
        worldPose.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        var vertices = frame.layerVertices();
        var counts = frame.layerVertexCounts();
        for (var layer : ChunkSectionLayer.values()) {
            var index = layer.ordinal();
            if (counts[index] == 0) continue;
            var layerVertices = vertices[index];
            output.submitCustomGeometry(
                    worldPose,
                    shaderPackRenderType(layer),
                    (pose, buffer) -> putShaderPackVertices(pose, buffer, layerVertices, counts[index])
            );
        }
    }

    private static RenderType shaderPackRenderType(ChunkSectionLayer layer) {
        return switch (layer) {
            case SOLID -> RenderTypes.solidMovingBlock();
            case CUTOUT -> RenderTypes.cutoutMovingBlock();
            case TRANSLUCENT -> RenderTypes.translucentMovingBlock();
        };
    }

    private static void putShaderPackVertices(PoseStack.Pose pose, VertexConsumer output,
                                               java.nio.ByteBuffer vertices, int vertexCount) {
        var stream = vertices.duplicate().order(vertices.order());
        stream.limit(stream.position() + vertexCount * BLOCK_VERTEX_STRIDE);
        for (var index = 0; index < vertexCount; index++) {
            var x = stream.getFloat();
            var y = stream.getFloat();
            var z = stream.getFloat();
            var u = stream.getFloat();
            var v = stream.getFloat();
            var red = colorChannel(stream.getFloat());
            var green = colorChannel(stream.getFloat());
            var blue = colorChannel(stream.getFloat());
            var alpha = colorChannel(stream.getFloat());
            var blockLight = Short.toUnsignedInt(stream.getShort());
            var skyLight = Short.toUnsignedInt(stream.getShort());
            var normalX = unpackNormal(stream.get());
            var normalY = unpackNormal(stream.get());
            var normalZ = unpackNormal(stream.get());
            stream.get();

            output.addVertex(pose, x, y, z)
                    .setColor(red, green, blue, alpha)
                    .setUv(u, v)
                    .setLight(blockLight | skyLight << 16)
                    .setNormal(pose, normalX, normalY, normalZ);
        }
    }

    private static int colorChannel(float value) {
        return Math.clamp(Math.round(value * 255.0f), 0, 255);
    }

    private static float unpackNormal(byte component) {
        return Math.max(-1.0f, component / 127.0f);
    }

    public ActiveEffect spawnOrRefresh(Identifier assetId, Vector3f position, String refreshKey) {
        if (refreshKey == null || refreshKey.isEmpty()) throw new IllegalArgumentException("Empty refresh key");
        var key = normalizedKey(assetId);
        var iterator = effects.iterator();
        while (iterator.hasNext()) {
            var effect = iterator.next();
            if (effect.assetKey().equals(key) && effect.refreshKey().equals(refreshKey)) {
                if (effect.isExpired()) {
                    iterator.remove();
                    break;
                }
                effect.refresh(refreshKey);
                effect.setPosition(position);
                return effect;
            }
        }
        var effect = spawn(assetId, position);
        effect.refresh(refreshKey);
        return effect;
    }

    public ActiveEffect spawn(Identifier assetId, Vector3f position) {
        var key = normalizedKey(assetId);
        var container = containerAssets.get(key);
        if (container != null) {
            var effect = new ActiveEffect(key, container, vfxRegistry, blockRegistry, operatorRegistry, position);
            effects.add(effect);
            return effect;
        }
        var graph = assets.get(key);
        if (graph == null) {
            var loaded = loadFromResourceManager(assetId);
            graph = loaded;
            container = containerAssets.get(key);
            if (container != null) {
                var effect = new ActiveEffect(key, container, vfxRegistry, blockRegistry, operatorRegistry, position);
                effects.add(effect);
                return effect;
            }
        }
        if (graph == null) {
            throw new IllegalArgumentException("no vfx graph asset: " + assetId);
        }
        var effect = new ActiveEffect(key, graph, vfxRegistry, position);
        effects.add(effect);
        return effect;
    }

    private static String normalizedKey(Identifier assetId) {
        var s = assetId.toString();
        return s.endsWith(".json") ? s.substring(0, s.length() - 5) : s;
    }

    private @Nullable Graph loadFromResourceManager(Identifier assetId) {
        var key = normalizedKey(assetId);
        var path = assetId.getPath().endsWith(".json") ? assetId.getPath() : assetId.getPath() + ".json";
        var fullId = Identifier.fromNamespaceAndPath(assetId.getNamespace(), path);
        try {
            var resourceManager = Minecraft.getInstance().getResourceManager();
            var resource = resourceManager.getResource(fullId).orElse(null);
            if (resource != null) {
                try (var reader = resource.openAsReader()) {
                    return decodeAsset(key, new Gson().fromJson(reader, JsonObject.class));
                }
            }
        } catch (Exception ignored) {
            // 资源管理器路径失败则走 classpath 兜底
        }
        var classpathPath = "/assets/" + fullId.getNamespace() + "/" + fullId.getPath();
        try (var stream = VfxGraphManager.class.getResourceAsStream(classpathPath)) {
            if (stream == null) {
                return null;
            }
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return decodeAsset(key, new Gson().fromJson(reader, JsonObject.class));
            }
        } catch (Exception exception) {
            LOGGER.debug("Unable to lazily load vfx graph asset {}", assetId, exception);
            return null;
        }
    }

    private @Nullable Graph decodeAsset(String key, JsonObject json) {
        if (isContainerJson(json)) {
            try {
                var system = new JsonVfxGraphCodec(metadata).decode(json);
                containerAssets.put(key, system);
                return null;
            } catch (Exception exception) {
                LOGGER.error("Unable to decode container vfx graph asset: {}", key, exception);
                return null;
            }
        }
        return assets.load(key, json);
    }

    private static boolean isContainerJson(JsonObject json) {
        try {
            return json.has(VfxGraphSchemaVersion.KIND_FIELD)
                    && "vfx".equals(json.get(VfxGraphSchemaVersion.KIND_FIELD).getAsString());
        } catch (Exception e) {
            return false;
        }
    }

    public ActiveEffect spawnFollow(Identifier assetId, Entity entity) {
        var effect = spawn(assetId, new Vector3f((float) entity.getX(), (float) entity.getY(), (float) entity.getZ()));
        effect.follow(entity);
        return effect;
    }

    public void stop(ActiveEffect effect) {
        effect.stop();
    }

    public void clearEffects() {
        effects.clear();
    }

    public int effectCount() {
        return effects.size();
    }

    public List<ActiveEffect> activeEffects() {
        return List.copyOf(effects);
    }

    public void reloadFromFile(Identifier assetId, Path file) throws IOException {
        reloadFromContent(assetId, Files.readString(file));
    }

    public void reloadFromContent(Identifier assetId, String jsonText) {
        var key = assetId.toString();
        var json = new Gson().fromJson(jsonText, JsonObject.class);
        if (isContainerJson(json)) {
            var system = new JsonVfxGraphCodec(metadata).decode(json);
            containerAssets.put(key, system);
            reloadEffects(key, system);
            return;
        }
        var graph = assets.load(key, json);
        if (graph != null) {
            reloadEffects(key, graph);
        }
    }

    public void registerAsset(Identifier assetId, JsonObject json) {
        var key = assetId.toString();
        if (isContainerJson(json)) {
            try {
                var system = new JsonVfxGraphCodec(metadata).decode(json);
                containerAssets.put(key, system);
                reloadEffects(key, system);
            } catch (Exception exception) {
                LOGGER.error("Unable to decode container vfx graph asset: {}", key, exception);
            }
            return;
        }
        var graph = assets.load(key, json);
        if (graph != null) {
            reloadEffects(key, graph);
        }
    }

    public void invalidateAll() {
        assets.invalidateAll();
        containerAssets.clear();
    }

    private void reloadEffects(String key, Graph graph) {
        for (var effect : effects) {
            if (effect.assetKey().equals(key)) {
                effect.reload(graph);
            }
        }
    }

    private void reloadEffects(String key, VfxSystem system) {
        for (var effect : effects) {
            if (effect.assetKey().equals(key)) {
                effect.reload(system);
            }
        }
    }
}
