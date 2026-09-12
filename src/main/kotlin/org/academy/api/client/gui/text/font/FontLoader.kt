package org.academy.api.client.gui.text.font

import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent
import org.academy.AcademyCraft
import org.academy.api.client.gui.glyph.bitmap.BitmapStrikeCache
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

@EventBusSubscriber(Dist.CLIENT)
object FontLoader : PreparableReloadListener {
    @SubscribeEvent
    fun onAddReloadListener(event: AddClientReloadListenersEvent) {
        event.addListener(AcademyCraft.academy("font_loader"), this)
    }

    override fun reload(
        currentReload: PreparableReloadListener.SharedState,
        taskExecutor: Executor,
        preparationBarrier: PreparableReloadListener.PreparationBarrier,
        reloadExecutor: Executor
    ): CompletableFuture<Void> {
        val manager = currentReload.resourceManager()
        val resources = manager.listResources("fonts") { MsdfFontService.isFont(it) }.keys

        MsdfFontService.setFontSearchOrder(resources.toList())

        val futures = resources.map { loadFont(it, taskExecutor) }.toTypedArray()
        return CompletableFuture.allOf(*futures).thenCompose { future ->
            // 字体重载后旧的 strike 缓存条目（旧字体 identityHash）永久滞留，需清空。
            BitmapStrikeCache.clear()
            preparationBarrier.wait(future)
        }
    }

    private fun loadFont(resource: Identifier, taskExecutor: Executor): CompletableFuture<Void> =
        CompletableFuture.runAsync({ MsdfFontService.loadFont(resource) }, taskExecutor)
}
