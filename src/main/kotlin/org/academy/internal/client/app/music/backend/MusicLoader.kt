package org.academy.internal.client.app.music.backend

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent
import org.academy.AcademyCraft
import org.academy.internal.client.app.music.data.MusicData
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

@EventBusSubscriber(Dist.CLIENT)
object MusicLoader : PreparableReloadListener {
    private val logger = AcademyCraft.getLogger()

    private val musicDataMapType: TypeToken<MutableMap<String, MusicData>> =
        object : TypeToken<MutableMap<String, MusicData>>() {}

    private val gson: Gson = GsonBuilder().create()
    private const val FILE_PATH = "musics/music_player.json"

    override fun reload(
        currentReload: PreparableReloadListener.SharedState,
        taskExecutor: Executor,
        preparationBarrier: PreparableReloadListener.PreparationBarrier,
        reloadExecutor: Executor
    ): CompletableFuture<Void> {
        val manager = currentReload.resourceManager()
        val future = CompletableFuture.supplyAsync({ loadAllMusicData(manager) }, taskExecutor)
        return future
            .thenCompose { preparationBarrier.wait(it) }
            .thenAcceptAsync({ applyToBackend(it) }, reloadExecutor)
    }

    private fun loadAllMusicData(manager: ResourceManager): MutableMap<String, MusicData> {
        val combinedMap = HashMap<String, MusicData>()
        for (namespace in manager.namespaces) loadFromNamespace(manager, namespace, combinedMap)
        return combinedMap
    }

    private fun loadFromNamespace(
        manager: ResourceManager,
        namespace: String,
        destination: MutableMap<String, MusicData>
    ) {
        val resources: MutableList<Resource> = manager.getResourceStack(AcademyCraft.custom(namespace, FILE_PATH))
        for (resource in resources) parseResource(resource, destination)
    }

    private fun parseResource(resource: Resource, destination: MutableMap<String, MusicData>) {
        try {
            InputStreamReader(resource.open(), StandardCharsets.UTF_8).use {
                val map = gson.fromJson(it, musicDataMapType)
                destination.putAll(map)
            }
        } catch (e: Exception) {
            logger.error(
                "Failed to parse '{}' from resource pack '{}'",
                FILE_PATH, resource.sourcePackId(), e
            )
        }
    }

    private fun applyToBackend(data: MutableMap<String, MusicData>) {
        MusicPlayerBackend.getInstance().updatePlaylistFromData(data, "All Resource Packs")
    }

    @SubscribeEvent
    fun onAddReloadListener(event: AddClientReloadListenersEvent) {
        event.addListener(AcademyCraft.academy("music_loader"), MusicLoader)
    }
}