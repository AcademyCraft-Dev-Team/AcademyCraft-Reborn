package org.academy.internal.client.app.music.session

import com.google.gson.GsonBuilder
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.bus.api.SubscribeEvent
import org.academy.AcademyCraft
import org.academy.api.client.gui.state.UiState
import org.academy.internal.client.app.music.netease.NeteaseCredentialManager
import org.academy.internal.client.app.music.qq.QqCredentialManager
import org.academy.internal.common.music.SharedTrackEntry
import org.academy.internal.common.network.MusicAccountPackets
import org.misaka.MisakaNetworkClient
import org.misaka.api.common.network.annotation.SubscribePacket
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 共享账号客户端：直链解析请求/响应（挂起 Future 表）、服主凭证上传、服务器歌单拉取喵。
 */
object SharedAccountClient {
    data class AccountStatus(
        val sharedEnabled: Boolean,
        val provider: String,
        val hasCredential: Boolean,
        val valid: Boolean,
        val expiresAtEpochSeconds: Long,
        val success: Boolean,
        val messageKey: String
    )

    data class ServerPlaylist(
        val enabled: Boolean,
        val mode: String,
        val tracks: List<SharedTrackEntry>
    )

    @Volatile
    var lastStatus: AccountStatus? = null
        private set

    @Volatile
    var serverPlaylist: ServerPlaylist? = null
        private set

    val accountUi = UiState(0)

    private val gson = GsonBuilder().create()
    private val pendingResolves = ConcurrentHashMap<String, CompletableFuture<List<String>>>()
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        NeoForge.EVENT_BUS.register(this)
        // register(Class) 只会注册静态方法，object 的 @SubscribePacket 是实例方法，必须传实例喵
        MisakaNetworkClient.NETWORK_MANAGER.register(SharedAccountClient)
    }

    /**
     * 请求服务器共享账号解析直链；未启用/无凭证/限流时返回异常完成的 Future 喵。
     */
    fun resolveTrack(provider: String, trackId: String): CompletableFuture<List<String>> {
        val key = "$provider:$trackId"
        return pendingResolves.computeIfAbsent(key) {
            val future = CompletableFuture<List<String>>()
                .orTimeout(20, TimeUnit.SECONDS)
                .whenComplete { _, _ -> pendingResolves.remove(key) }
            MisakaNetworkClient.send(
                MusicAccountPackets.ResolveRequestPacket(provider, trackId)
            )
            future
        }
    }

    /**
     * 将玩家本地已登录的凭证上传为服务器共享账号（服务端校验 OP）喵。
     */
    fun uploadLocalCredential(provider: String): Boolean {
        val payload = when (provider.lowercase()) {
            "qq" -> {
                val credential = QqCredentialManager.getCredential() ?: return false
                val root = com.google.gson.JsonObject()
                root.addProperty("musicid", credential.musicId)
                root.addProperty("musickey", credential.musicKey)
                root.addProperty("keyExpiresIn", credential.keyExpiresIn)
                root.addProperty("musickeyCreateTime", credential.musicKeyCreateTime)
                gson.toJson(root)
            }

            "netease" -> {
                val cookie = NeteaseCredentialManager.getEffectiveCookie()
                if (cookie.isBlank()) return false
                val root = com.google.gson.JsonObject()
                root.addProperty("cookie", cookie)
                gson.toJson(root)
            }

            else -> return false
        }
        MisakaNetworkClient.send(
            MusicAccountPackets.AccountActionPacket(
                MusicAccountPackets.AccountAction.UPLOAD, provider, payload
            )
        )
        return true
    }

    fun clearServerCredential(provider: String) {
        MisakaNetworkClient.send(
            MusicAccountPackets.AccountActionPacket(
                MusicAccountPackets.AccountAction.CLEAR, provider, ""
            )
        )
    }

    fun queryStatus(provider: String) {
        MisakaNetworkClient.send(
            MusicAccountPackets.AccountActionPacket(
                MusicAccountPackets.AccountAction.STATUS, provider, ""
            )
        )
    }

    fun requestPlaylist() {
        MisakaNetworkClient.send(MusicAccountPackets.PlaylistRequestPacket())
    }

    @SubscribePacket
    fun onResolveResponse(packet: MusicAccountPackets.ResolveResponsePacket) {
        val key = "${packet.provider()}:${packet.trackId()}"
        val future = pendingResolves.remove(key) ?: return
        if (packet.error().isBlank()) {
            future.complete(packet.urls())
        } else {
            future.completeExceptionally(IOException("resolve:${packet.error()}"))
        }
    }

    @SubscribePacket
    fun onAccountStatus(packet: MusicAccountPackets.AccountStatusPacket) {
        lastStatus = AccountStatus(
            packet.sharedEnabled(),
            packet.provider(),
            packet.hasCredential(),
            packet.valid(),
            packet.expiresAtEpochSeconds(),
            packet.success(),
            packet.messageKey()
        )
        bumpUi()
    }

    @SubscribePacket
    fun onServerPlaylist(packet: MusicAccountPackets.ServerPlaylistPacket) {
        serverPlaylist = ServerPlaylist(packet.enabled(), packet.mode(), packet.tracks())
        bumpUi()
    }

    @SubscribeEvent
    fun onLoggingIn(@Suppress("unused") event: ClientPlayerNetworkEvent.LoggingIn) {
        requestPlaylist()
    }

    @SubscribeEvent
    fun onLoggingOut(@Suppress("unused") event: ClientPlayerNetworkEvent.LoggingOut) {
        lastStatus = null
        pendingResolves.values.forEach { it.completeExceptionally(IOException("disconnected")) }
        pendingResolves.clear()
    }

    private fun bumpUi() {
        Minecraft.getInstance().execute { accountUi.value += 1 }
    }
}
