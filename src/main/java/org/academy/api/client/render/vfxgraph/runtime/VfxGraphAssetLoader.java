package org.academy.api.client.render.vfxgraph.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import org.academy.AcademyCraft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 图资产游戏内加载（M15-02）：把 {@code assets/<ns>/vfxgraph/*.json} 解码进
 * {@link VfxGraphManager} 的 {@code GraphAssets}（复用 M7 迁移链），F3+T / 资源重载触发，
 * 同时刷新使用中的存活效果（M15-05 热重载第一通道）。
 *
 * <p>线程约定（Bug 修复）：后台线程（taskExecutor）只解析 JSON 到局部 map；
 * 经 {@link PreparationBarrier#wait} 回主线程后，再一次性 apply 到管理器
 * （写 {@code GraphAssets} 缓存与存活效果），避免与主线程 tick/render 并发写非线程安全集合。</p>
 */
@EventBusSubscriber(Dist.CLIENT)
public final class VfxGraphAssetLoader implements PreparableReloadListener {
    public static final VfxGraphAssetLoader INSTANCE = new VfxGraphAssetLoader();

    private static final String DIRECTORY = "vfxgraph";
    private static final Gson GSON = new Gson();

    private VfxGraphAssetLoader() {
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddClientReloadListenersEvent event) {
        event.addListener(AcademyCraft.academy("vfx_graph_assets"), INSTANCE);
    }

    @Override
    public CompletableFuture<Void> reload(
            SharedState currentReload,
            Executor taskExecutor,
            PreparationBarrier preparationBarrier,
            Executor reloadExecutor
    ) {
        var resources = currentReload.resourceManager().listResources(DIRECTORY,
                path -> path.getPath().endsWith(".json") && !path.getPath().endsWith(".editor.json"));
        return CompletableFuture.supplyAsync(() -> {
                    // 后台线程：只解析，不改任何管理器状态
                    Map<Identifier, JsonObject> parsed = new LinkedHashMap<>();
                    for (var entry : resources.entrySet()) {
                        try (var reader = entry.getValue().openAsReader()) {
                            parsed.put(entry.getKey(), GSON.fromJson(reader, JsonObject.class));
                        } catch (Exception exception) {
                            AcademyCraft.getLogger().error("Unable to parse vfx graph asset: " + entry.getKey(), exception);
                        }
                    }
                    return parsed;
                }, taskExecutor)
                .thenCompose(preparationBarrier::wait)
                .thenAccept(VfxGraphAssetLoader::applyOnMainThread);
    }

    private static void applyOnMainThread(Map<Identifier, JsonObject> parsed) {
        // 主线程：一次性 apply（invalidateAll + 逐条注册 + 刷新存活效果）
        // 防御：解析为空（如部分重载阶段资源暂不可见）时不清空已有资产，避免误删已加载缓存。
        if (parsed.isEmpty()) {
            return;
        }
        var manager = VfxGraphManager.INSTANCE;
        manager.invalidateAll();
        for (var entry : parsed.entrySet()) {
            // 键统一去掉 .json：listResources 返回的 identifier 带扩展名，
            // 而 spawn/命令 拼的是不带扩展名的 id（academy:vfxgraph/<name>）
            var id = entry.getKey();
            if (id.getPath().endsWith(".json")) {
                id = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring(0, id.getPath().length() - 5));
            }
            manager.registerAsset(id, entry.getValue());
        }
    }
}
