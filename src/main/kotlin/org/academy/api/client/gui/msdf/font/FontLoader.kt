package org.academy.api.client.gui.msdf.font

import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent
import org.academy.AcademyCraft.academy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

@EventBusSubscriber(Dist.CLIENT)
object FontLoader : PreparableReloadListener {
    override fun reload(
        currentReload: PreparableReloadListener.SharedState,
        taskExecutor: Executor,
        preparationBarrier: PreparableReloadListener.PreparationBarrier,
        reloadExecutor: Executor
    ): CompletableFuture<Void> {
        val manager = currentReload.resourceManager()
        val resources = manager.listResources("fonts", MsdfFontService::isFont).keys

        MsdfFontService.setFontSearchOrder(ArrayList(resources))

        val futuresStream = resources.map { resource -> loadFont(resource, taskExecutor) }
        return CompletableFuture
            .allOf(*futuresStream.toTypedArray())
            .thenCompose(preparationBarrier::wait)
    }

    private fun loadFont(resource: Identifier, taskExecutor: Executor): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            MsdfFontService.loadFont(resource)
        }, taskExecutor)
    }

    @SubscribeEvent
    fun onAddReloadListener(event: AddClientReloadListenersEvent) {
        event.addListener(academy("font_loader"), FontLoader)
    }
}