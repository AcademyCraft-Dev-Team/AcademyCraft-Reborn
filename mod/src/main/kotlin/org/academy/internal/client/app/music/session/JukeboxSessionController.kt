package org.academy.internal.client.app.music.session

import com.google.gson.GsonBuilder
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.common.NeoForge
import org.academy.AcademyCraft
import org.academy.api.client.gui.state.UiState
import org.academy.api.client.vanilla.MainLoopEvent
import org.academy.internal.client.app.music.backend.MusicPlayerBackend
import org.academy.internal.client.app.music.common.PlaybackController
import org.academy.internal.common.music.PlaybackTimeline
import org.academy.internal.common.music.QueueSnapshot
import org.academy.internal.common.music.SharedTrackEntry
import org.academy.internal.common.network.MusicJukeboxPackets
import org.misaka.MisakaNetworkClient
import org.misaka.api.common.network.annotation.SubscribePacket
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * 全服点播客户端会话：订阅/静音（静音偏好本地持久化），接收点播状态并经
 * [SharedPlaybackFollower] 对齐播放。与音乐室互斥时房间优先，退出房间后自动重新接管喵。
 */
object JukeboxSessionController {
    data class JukeboxState(
        val enabled: Boolean,
        val mode: Int,
        val queue: QueueSnapshot,
        val voteActive: Boolean,
        val voteCount: Int,
        val voteThreshold: Int
    ) {
        val timeline: PlaybackTimeline? get() = currentTimeline
    }

    @Volatile
    var state: JukeboxState? = null
        private set

    @Volatile
    var subscribed: Boolean = loadSubscribedPreference()
        private set

    val jukeboxStateUi = UiState(0)

    @Volatile
    private var currentTimeline: PlaybackTimeline? = null

    @Volatile
    private var lastSync: MusicJukeboxPackets.SyncPacket? = null

    private val follower = SharedPlaybackFollower()

    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * 惰性解析：若写成字段，[subscribed] 的初始化会早于该字段而读到 null（启动时报 NPE 并丢失静音偏好）喵。
     */
    private val preferenceFile: java.nio.file.Path
        get() = FMLPaths.GAMEDIR.get()
            .resolve("academy_music")
            .resolve("jukebox.json")

    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        NeoForge.EVENT_BUS.register(this)
        // register(Class) 只会注册静态方法，object 的 @SubscribePacket 是实例方法，必须传实例喵
        MisakaNetworkClient.NETWORK_MANAGER.register(JukeboxSessionController)
    }

    fun setSubscribed(value: Boolean) {
        subscribed = value
        saveSubscribedPreference(value)
        MisakaNetworkClient.send(MusicJukeboxPackets.ActionPacket.subscribe(value))
        if (!value && RoomSessionController.roomState == null) {
            // 静音：让出播放权给本地播放；在音乐室中则不动房间播放喵.
            releasePlayback()
        }
        // 开启收听时不必本地抢先接管，等服务端回推状态后再按时间线落轨，避免打断正在播放的内容喵.
        bumpUi()
    }

    fun requestTrack(entry: SharedTrackEntry) {
        MisakaNetworkClient.send(MusicJukeboxPackets.ActionPacket.request(entry))
    }

    fun voteSkip() {
        MisakaNetworkClient.send(MusicJukeboxPackets.ActionPacket.voteSkip())
    }

    fun requestState() {
        // REQUEST_STATE 只回推状态，不影响订阅偏好；subscribe(subscribed) 会在静音时顺带清空服务端订阅喵.
        MisakaNetworkClient.send(MusicJukeboxPackets.ActionPacket.requestState())
    }

    fun expectedPlaying(): Boolean = follower.expectedPlaying()

    fun expectedPositionSeconds(): Float = follower.expectedPositionSeconds()

    /**
     * 未静音、点播已启用、不在音乐室且服务端确有曲目在播——此时点播占用播放通道喵。
     */
    fun isActivelyPlaying(): Boolean {
        if (!subscribed || RoomSessionController.roomState != null) return false
        if (state?.enabled != true) return false
        if (lastSync?.timeline() == null) return false
        return follower.expectedPlaying()
    }

    @SubscribePacket
    fun onJukeboxSync(packet: MusicJukeboxPackets.SyncPacket) {
        currentTimeline = packet.timeline()
        lastSync = packet
        state = JukeboxState(
            packet.enabled(),
            packet.mode(),
            packet.queue(),
            packet.voteActive(),
            packet.voteCount(),
            packet.voteThreshold()
        )
        bumpUi()

        if (RoomSessionController.roomState != null) {
            // 音乐室优先：不接管点播时间线，但要清理跟随器避免退出房间后沿用陈旧进度喵.
            follower.reset()
            return
        }
        if (!subscribed || !packet.enabled() || packet.timeline() == null) {
            // 未订阅/未启用/服务端空闲：若此前正被点播占用则交还播放权喵.
            if (follower.hasTimeline) releasePlayback()
            return
        }
        // 未静音时全服点播为最高优先：无条件接管共享播放喵.
        MusicPlayerBackend.getInstance().setPlaybackController(PlaybackController.SHARED)
        follower.apply(packet.timeline(), packet.serverGameTime())
    }

    @SubscribeEvent
    fun onLoggingIn(@Suppress("unused") event: ClientPlayerNetworkEvent.LoggingIn) {
        // 把本地静音偏好同步给服务端：订阅时服务端会顺带回推当前点播状态喵.
        if (subscribed) {
            MisakaNetworkClient.send(MusicJukeboxPackets.ActionPacket.subscribe(true))
        } else {
            MisakaNetworkClient.send(MusicJukeboxPackets.ActionPacket.subscribe(false))
            requestState()
        }
    }

    @SubscribeEvent
    fun onLoggingOut(@Suppress("unused") event: ClientPlayerNetworkEvent.LoggingOut) {
        releaseLocally()
    }

    @SubscribeEvent
    fun onMainLoop(@Suppress("unused") event: MainLoopEvent) {
        if (!subscribed || state?.enabled != true) return
        if (RoomSessionController.roomState != null) return
        val sync = lastSync ?: return
        if (sync.timeline() == null) return
        val backend = MusicPlayerBackend.getInstance()
        if (backend.playbackController != PlaybackController.SHARED) {
            // 刚退出音乐室或本地播放抢占后，未静音时点播最高优先，重新接管时间线喵.
            backend.setPlaybackController(PlaybackController.SHARED)
            follower.apply(sync.timeline(), sync.serverGameTime())
        } else {
            follower.correctDrift()
        }
    }

    /**
     * 让出共享播放：仅当点播确实在控制播放时才停止音频，避免误伤音乐室/本地播放喵。
     */
    private fun releasePlayback() {
        val backend = MusicPlayerBackend.getInstance()
        if (follower.hasTimeline) {
            follower.reset()
            backend.stop()
        } else {
            follower.reset()
        }
        backend.setPlaybackController(PlaybackController.LOCAL)
        bumpUi()
    }

    private fun releaseLocally() {
        follower.reset()
        MusicPlayerBackend.getInstance().setPlaybackController(PlaybackController.LOCAL)
        bumpUi()
    }

    private fun loadSubscribedPreference(): Boolean {
        return try {
            if (!Files.exists(preferenceFile)) return true
            val root = com.google.gson.JsonParser.parseReader(
                Files.newBufferedReader(preferenceFile, StandardCharsets.UTF_8)
            ).asJsonObject
            !root.has("subscribed") || root.get("subscribed").asBoolean
        } catch (exception: Exception) {
            AcademyCraft.LOGGER.warn("Failed to load jukebox preference", exception)
            true
        }
    }

    private fun saveSubscribedPreference(value: Boolean) {
        try {
            Files.createDirectories(preferenceFile.parent)
            val root = com.google.gson.JsonObject()
            root.addProperty("subscribed", value)
            Files.newBufferedWriter(preferenceFile, StandardCharsets.UTF_8).use {
                gson.toJson(root, it)
            }
        } catch (exception: Exception) {
            AcademyCraft.LOGGER.warn("Failed to save jukebox preference", exception)
        }
    }

    private fun bumpUi() {
        Minecraft.getInstance().execute { jukeboxStateUi.value += 1 }
    }
}
