package org.academy.api.client.render.vfxgraph.runtime;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.academy.AcademyCraft;
import org.academy.api.client.render.graph.assets.GraphAssets;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.serialize.JsonGraphCodec;
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

/**
 * 客户端 VFX 图效果管理器（M15-01）：tick/render 循环接入，spawn/停止，资产重载，池化/剔除。
 *
 * <p>单例（仿 {@code VfxManager.INSTANCE}）。持有共享 [VfxNodeRegistry]（游戏内首次接线
 * {@link VfxNodes#registerAll}）与 [GraphAssets]（复用 M7 缓存 + 迁移链）、按拓扑共享的渲染器池。
 * M27 支持容器资产（{@code kind:"vfx"} → [VfxSystem] + [VfxSystemSimulator]），扁平资产（旧 schema）仍可加载。
 * 生命周期由 {@code AcademyCraftClient} 接入（init/tick/close）。</p>
 */
public final class VfxGraphManager {
    public static final VfxGraphManager INSTANCE = new VfxGraphManager();

    /** DirStrike 技能替换用图资产（M20，A4）；缺失时 spawn 抛异常被客户端兜底忽略。 */
    public static final Identifier DIR_STRIKE_ASSET = Identifier.fromNamespaceAndPath("academy", "vfxgraph/skill_dirstrike");

    private final SimpleNodeRegistry metadata = new SimpleNodeRegistry();
    private final VfxNodeRegistry vfxRegistry = new VfxNodeRegistry();
    private final VfxBlockRegistry blockRegistry = new VfxBlockRegistry();
    private final VfxOperatorRegistry operatorRegistry = new VfxOperatorRegistry();
    private final GraphAssets assets;
    private final Map<String, VfxSystem> containerAssets = new LinkedHashMap<>();
    private final EffectBudget budget = new EffectBudget();
    private final Set<ActiveEffect> effects = new LinkedHashSet<>();
    private final Map<List<RenderSpec>, VfxGraphRenderer> rendererPool = new LinkedHashMap<>();
    private GraphFileWatcher fileWatcher;
    private boolean initialized;
    private long lastRenderNanos = -1;
    /** 最近一帧的相机（供 glow pass 复用，与主渲染同帧）。 */
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

    /** 由 {@code AcademyCraftClient.initRender()} 调用。 */
    public void init() {
        initialized = true;
    }

    /** 由 {@code AcademyCraftClient.onClientStopped()} 调用。 */
    public void close() {
        stopFileWatcher();
        effects.clear();
        for (var renderer : rendererPool.values()) {
            renderer.close();
        }
        rendererPool.clear();
        initialized = false;
    }

    /** dev 模式启动文件监听（M15-05）。 */
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
            org.academy.AcademyCraft.getLogger().warn("Unable to start vfx graph file watcher at {}", root, exception);
        }
    }

    public void stopFileWatcher() {
        if (fileWatcher != null) {
            fileWatcher.close();
            fileWatcher = null;
        }
    }

    /** 逐 effect tick。移除已停止或跟随实体已移除的效果。 */
    public void tick(float dt) {
        if (!initialized) {
            return;
        }
        var iterator = effects.iterator();
        while (iterator.hasNext()) {
            var effect = iterator.next();
            // 粒子上限（M15-06）：达到上限时冻结该帧模拟（不再产新粒），已存在粒子保留渲染（Bug 修复）
            if (!budget.canSpawnMore(effect.effect().buffer().count())) {
                continue;
            }
            if (effect.tick(dt)) {
                iterator.remove();
            }
        }
    }

    /** 渲染所有可见效果到目标纹理（不清屏，叠加世界变换）。每帧以真实帧时间步进模拟（平滑，不锁 20Hz tick）；游戏暂停时冻结。
     *  glow 拓扑（{@code BILLBOARD_GLOW}）效果在此渲出实心 additive 主体，另由 {@link #renderGlowFrame} 渲进 bloom 输入形成光晕。 */
    public void renderFrame(GpuTextureView target, @Nullable GpuTextureView depth, GraphCamera camera) {
        if (!initialized || effects.isEmpty()) {
            return;
        }
        lastCamera = camera;
        boolean paused = Minecraft.getInstance().isPaused();
        long now = System.nanoTime();
        float dt = paused ? 0f : lastRenderNanos <= 0 ? 1f / 60f : Math.min((now - lastRenderNanos) / 1e9f, 0.1f);
        lastRenderNanos = now;
        var iterator = effects.iterator();
        while (iterator.hasNext()) {
            var effect = iterator.next();
            if (!budget.canSpawnMore(effect.effect().buffer().count())) {
                continue;
            }
            if (!paused) {
                if (effect.tick(dt)) {
                    iterator.remove();
                    continue;
                }
            }
            if (!budget.shouldRender(camera.position(), effect.position())) {
                continue;
            }
            if (!budget.sphereInFrustum(camera.projection(), camera.viewRotation(), camera.position(), effect.position())) {
                continue;
            }
            var renderer = rendererPool.computeIfAbsent(effect.specs(), k -> new VfxGraphRenderer());
            effect.render(target, depth, camera, renderer, false, false);
        }
    }

    /** 把 glow 拓扑效果渲进 bloom 输入（additive，不清屏，只画 GLOW 输出规格）。由 {@code GlowEffect.process()} 调用。 */
    public void renderGlowFrame(GpuTextureView color, @Nullable GpuTextureView depth) {
        if (!initialized || effects.isEmpty()) {
            return;
        }
        var camera = lastCamera;
        if (camera == null) {
            return;
        }
        for (var effect : effects) {
            if (effect.specs().stream().noneMatch(RenderSpec::feedsBloom)) {
                continue;
            }
            if (!budget.canSpawnMore(effect.effect().buffer().count())) {
                continue;
            }
            if (!budget.shouldRender(camera.position(), effect.position())) {
                continue;
            }
            if (!budget.sphereInFrustum(camera.projection(), camera.viewRotation(), camera.position(), effect.position())) {
                continue;
            }
            var renderer = rendererPool.computeIfAbsent(effect.specs(), k -> new VfxGraphRenderer());
            effect.render(color, depth, camera, renderer, false, true);
        }
    }

    /** 是否存在活的 glow 拓扑效果（供 {@code GlowEffect.process()} 决定是否跑 bloom 帧）。 */
    public boolean hasGlowData() {
        if (!initialized) {
            return false;
        }
        for (var effect : effects) {
            if (effect.specs().stream().anyMatch(RenderSpec::feedsBloom)) {
                return true;
            }
        }
        return false;
    }

    /** 按图资产 id spawn 效果到世界坐标。键统一去掉 .json，兼容带/不带扩展名的写法。 */
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
            // 懒加载兜底：重载监听可能尚未装载（或未命中），直接从资源管理器/classpath 读取
            var loaded = loadFromResourceManager(assetId);
            graph = loaded;
        }
        if (graph == null) {
            throw new IllegalArgumentException("no vfx graph asset: " + assetId);
        }
        var effect = new ActiveEffect(key, graph, vfxRegistry, position);
        effects.add(effect);
        return effect;
    }

    /** 资产键统一为不带 .json 的 identifier 字符串（与命令/API 入参一致）。 */
    private static String normalizedKey(Identifier assetId) {
        var s = assetId.toString();
        return s.endsWith(".json") ? s.substring(0, s.length() - 5) : s;
    }

    /**
     * 从游戏资源管理器或 classpath 按 identifier（如 {@code academy:vfxgraph/minimal_burst}）读取并缓存图资产。
     * 容器 schema（{@code kind:"vfx"}）解码进 [VfxSystem] 缓存，否则进扁平 [GraphAssets]。
     *
     * <p>资源管理器/classpath 查找需带 .json 完整路径（getResource 不自动补扩展名），
     * 缓存键仍用去掉 .json 的 id。dev 模式下资源管理器可能未挂载 {@code src/main/resources}，
     * 故 classpath 兜底。</p>
     */
    private @Nullable Graph loadFromResourceManager(Identifier assetId) {
        var key = normalizedKey(assetId);
        var path = assetId.getPath().endsWith(".json") ? assetId.getPath() : assetId.getPath() + ".json";
        var fullId = Identifier.fromNamespaceAndPath(assetId.getNamespace(), path);
        try {
            var resourceManager = Minecraft.getInstance().getResourceManager();
            var resource = resourceManager.getResource(fullId).orElse(null);
            if (resource != null) {
                try (var reader = resource.openAsReader()) {
                    return decodeAsset(key, new com.google.gson.Gson().fromJson(reader, JsonObject.class));
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
            try (var reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
                return decodeAsset(key, new com.google.gson.Gson().fromJson(reader, JsonObject.class));
            }
        } catch (Exception exception) {
            AcademyCraft.getLogger().debug("Unable to lazily load vfx graph asset {}", assetId, exception);
            return null;
        }
    }

    /** 按 schema 解码并缓存：容器进 containerAssets，扁平进 GraphAssets。返回扁平 Graph（容器返回 null）。 */
    private @Nullable Graph decodeAsset(String key, JsonObject json) {
        if (isContainerJson(json)) {
            try {
                var system = new JsonVfxGraphCodec(metadata).decode(json);
                containerAssets.put(key, system);
                return null;
            } catch (Exception exception) {
                AcademyCraft.getLogger().error("Unable to decode container vfx graph asset: " + key, exception);
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

    /** spawn 并跟随实体（每 tick 对齐实体位置，实体移除即停）。 */
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

    /** 从磁盘文件加载/重载单个资产（dev 热重载，M15-05）。 */
    public void reloadFromFile(Identifier assetId, Path file) throws IOException {
        reloadFromContent(assetId, Files.readString(file));
    }

    /** 以已读取的 JSON 文本加载/重载单个资产（供主线程/后台线程调用）。 */
    public void reloadFromContent(Identifier assetId, String jsonText) {
        var key = assetId.toString();
        var json = new com.google.gson.Gson().fromJson(jsonText, JsonObject.class);
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

    /** 注册（或重载）一个资产并刷新使用它的存活效果。解码失败则跳过（不中断重载）。 */
    public void registerAsset(Identifier assetId, JsonObject json) {
        var key = assetId.toString();
        if (isContainerJson(json)) {
            try {
                var system = new JsonVfxGraphCodec(metadata).decode(json);
                containerAssets.put(key, system);
                reloadEffects(key, system);
            } catch (Exception exception) {
                AcademyCraft.getLogger().error("Unable to decode container vfx graph asset: " + key, exception);
            }
            return;
        }
        var graph = assets.load(key, json);
        if (graph != null) {
            reloadEffects(key, graph);
        }
    }

    /** 全量失效（资源重载触发，F3+T）。 */
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
