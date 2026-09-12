package org.academy.internal.client.app.music.backend

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import org.academy.AcademyCraft
import org.academy.api.client.gui.state.UiState
import org.academy.api.client.resources.R
import org.academy.api.client.vanilla.MainLoopEvent
import org.academy.internal.client.app.music.data.MusicInfo
import org.academy.internal.client.app.music.data.MusicSource
import org.academy.internal.client.app.music.netease.NeteaseAudioCache
import org.academy.internal.client.app.music.netease.NeteaseCredentialManager
import org.academy.internal.client.app.music.netease.NeteaseLoginService
import org.academy.internal.client.app.music.qq.QqAudioCache
import org.academy.internal.client.app.music.qq.QqCredentialManager
import org.academy.internal.client.app.music.qq.QqLoginService
import org.academy.internal.common.music.provider.AudioFormat
import org.academy.internal.common.music.provider.HttpUtil
import org.academy.internal.common.music.provider.MusicProviders
import org.academy.internal.common.music.provider.ProviderCredential
import org.academy.internal.common.network.MusicSyncPackets
import org.misaka.MisakaNetworkClient
import org.misaka.api.common.network.annotation.SubscribePacket
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

object OnlineMusicManager {
    enum class Provider(val storageName: String) {
        QQ("qq"),
        NETEASE("netease")
    }

    enum class LoginState {
        IDLE,
        FETCHING,
        WAITING,
        SUCCESS,
        FAILED,
        EXPIRED
    }

    data class SearchEntry(
        val provider: Provider,
        val id: String,
        val title: String,
        val artist: String,
        val durationSeconds: Int,
        val vip: Boolean,
        val artworkUrl: String = ""
    )

    private data class StoredTrack(
        val provider: String = "",
        val id: String = "",
        val title: String = "",
        val artist: String = "",
        val durationSeconds: Int = 0,
        val vip: Boolean = false,
        val artworkUrl: String = ""
    )

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val storageFile = FMLPaths.GAMEDIR.get()
        .resolve("academy_music")
        .resolve("playlist.json")
    private val storedTracks = mutableListOf<StoredTrack>()
    private val revisionCounter = AtomicInteger()
    private var initialized = false
    private var loginProvider: Provider? = null
    private var qqQrSig = ""
    private var neteaseUniKey = ""
    private var lastLoginPoll = 0L
    private var loginFuture: CompletableFuture<*>? = null

    @Volatile
    var loginState = LoginState.IDLE
        private set

    @Volatile
    var selectedProvider = Provider.QQ
        private set

    @Volatile
    var searchResults: List<SearchEntry> = emptyList()
        private set

    @Volatile
    var status: String = ""
        private set

    @Volatile
    var qrBytes: ByteArray? = null
        private set

    val revisionState = UiState(0)

    val revision: Int
        get() = revisionCounter.get()

    private fun bumpRevision() {
        revisionCounter.incrementAndGet()
        Minecraft.getInstance().execute { revisionState.value = revisionCounter.get() }
    }

    fun init() {
        if (initialized) return
        initialized = true
        NeoForge.EVENT_BUS.register(this)
        QqCredentialManager.init()
        NeteaseCredentialManager.init()
        // register(Class) 只会注册静态方法，object 的 @SubscribePacket 是实例方法，必须传实例喵
        MisakaNetworkClient.NETWORK_MANAGER.register(OnlineMusicManager)
        org.academy.internal.client.app.music.session.RoomSessionController.init()
        org.academy.internal.client.app.music.session.JukeboxSessionController.init()
        org.academy.internal.client.app.music.session.SharedAccountClient.init()
        loadPlaylist()
    }

    fun selectProvider(provider: Provider) {
        selectedProvider = provider
        loginProvider = null
        qrBytes = null
        loginFuture = null
        loginState = LoginState.IDLE
        searchResults = emptyList()
        status = providerLabel(provider)
        bumpRevision()
    }

    fun search(query: String) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        val provider = selectedProvider
        status = "${providerLabel(provider)}：搜索中…"
        searchResults = emptyList()
        bumpRevision()
        CompletableFuture.supplyAsync({
            val service = MusicProviders.byName(provider.storageName).orElseThrow()
            service.search(normalized, playerCredential(provider)).map {
                SearchEntry(
                    provider,
                    it.trackId,
                    it.title,
                    it.artist,
                    it.durationSeconds,
                    it.vip,
                    it.artworkUrl
                )
            }
        }, AcademyCraft.executorService).whenComplete { results, throwable ->
            Minecraft.getInstance().execute {
                if (provider != selectedProvider) return@execute
                if (throwable != null) {
                    status = "搜索失败：${rootMessage(throwable)}"
                    searchResults = emptyList()
                } else {
                    searchResults = results ?: emptyList()
                    status = "${providerLabel(provider)}：${searchResults.size} 条结果"
                }
                bumpRevision()
            }
        }
    }

    /**
     * 收藏服务器歌单/共享播放中的曲目到个人播放列表喵。
     */
    fun addSharedEntry(entry: org.academy.internal.common.music.SharedTrackEntry) {
        val provider = providerByName(entry.provider()) ?: return
        add(
            SearchEntry(
                provider,
                entry.trackId(),
                entry.title(),
                entry.artist(),
                entry.durationSeconds(),
                entry.vip(),
                entry.artworkUrl()
            ),
            false
        )
    }

    fun add(entry: SearchEntry, playNow: Boolean = false) {
        val stored = entry.toStoredTrack()
        synchronized(storedTracks) {
            storedTracks.removeIf { it.provider == stored.provider && it.id == stored.id }
            storedTracks.add(stored)
            savePlaylist()
        }
        val info = createMusicInfo(stored)
        MusicPlayerBackend.getInstance().addOnlineTrack(info, false)
        status = "正在缓存：${entry.title}"
        bumpRevision()
        cache(entry.provider, entry.id).whenComplete { _, throwable ->
            Minecraft.getInstance().execute {
                if (throwable != null) {
                    status = "缓存失败：${rootMessage(throwable)}"
                } else {
                    status = "已加入播放列表：${entry.title}"
                    if (playNow) MusicPlayerBackend.getInstance().addOnlineTrack(info, true)
                }
                bumpRevision()
            }
        }
    }

    fun remove(info: MusicInfo) {
        if (info.provider == "local" || info.externalId.isBlank()) return
        synchronized(storedTracks) {
            storedTracks.removeIf { it.provider == info.provider && it.id == info.externalId }
            savePlaylist()
        }
        MusicPlayerBackend.getInstance().removeOnlineTrack(info.provider, info.externalId)
        status = "已从播放列表移除：${info.name}"
        bumpRevision()
    }

    fun onPlayError(message: String) {
        status = "播放失败：$message"
        bumpRevision()
    }

    /**
     * 在音乐播放器状态行展示一条提示（复用现有状态文本通道）喵。
     */
    fun notifyStatus(message: String) {
        status = message
        bumpRevision()
    }

    fun startLogin() {
        if (loginFuture?.isDone == false) return
        val provider = selectedProvider
        loginProvider = provider
        qrBytes = null
        loginState = LoginState.FETCHING
        status = "${providerLabel(provider)}：正在获取登录二维码…"
        bumpRevision()
        loginFuture = when (provider) {
            Provider.QQ -> QqLoginService.fetchQrCode().whenComplete { session, throwable ->
                Minecraft.getInstance().execute {
                    if (loginProvider != provider) return@execute
                    if (throwable != null || session == null) {
                        status = "QQ 音乐登录失败：${rootMessage(throwable)}"
                        loginState = LoginState.FAILED
                    } else {
                        qqQrSig = session.qrsig()
                        qrBytes = session.imageBytes()
                        status = "QQ 音乐：请扫码登录"
                        loginState = LoginState.WAITING
                        lastLoginPoll = 0L
                    }
                    bumpRevision()
                }
            }

            Provider.NETEASE -> NeteaseLoginService.fetchQrCode().whenComplete { session, throwable ->
                Minecraft.getInstance().execute {
                    if (loginProvider != provider) return@execute
                    if (throwable != null || session == null) {
                        status = "网易云音乐登录失败：${rootMessage(throwable)}"
                        loginState = LoginState.FAILED
                    } else {
                        neteaseUniKey = session.uniKey()
                        qrBytes = session.imageBytes()
                        status = "网易云音乐：请扫码登录"
                        loginState = LoginState.WAITING
                        lastLoginPoll = 0L
                    }
                    bumpRevision()
                }
            }
        }
    }

    fun logout() {
        when (selectedProvider) {
            Provider.QQ -> QqCredentialManager.clear()
            Provider.NETEASE -> NeteaseLoginService.logout()
        }
        qrBytes = null
        loginProvider = null
        loginFuture = null
        loginState = LoginState.IDLE
        status = "${providerLabel(selectedProvider)}：已退出登录"
        bumpRevision()
    }

    fun cancelLogin() {
        loginProvider = null
        qrBytes = null
        loginFuture = null
        loginState = LoginState.IDLE
        status = ""
        bumpRevision()
    }

    fun refreshLogin() {
        if (loginFuture?.isDone == false) return
        loginProvider = null
        qrBytes = null
        startLogin()
    }

    fun accountDisplayName(provider: Provider = selectedProvider): String = when (provider) {
        Provider.QQ -> {
            val id = QqCredentialManager.getCredential().musicId
            if (id.isBlank()) providerLabel(provider) else "${providerLabel(provider)} · ${maskAccount(id)}"
        }

        Provider.NETEASE -> {
            val credential = NeteaseCredentialManager.getCredential()
            val nickname = credential.nickname
            nickname.ifBlank {
                val uid = credential.uid
                if (uid.isBlank()) providerLabel(provider) else "${providerLabel(provider)} · ${maskAccount(uid)}"
            }
        }
    }

    fun accountAvatarUrl(provider: Provider = selectedProvider): String? = when (provider) {
        Provider.QQ -> null
        Provider.NETEASE -> NeteaseCredentialManager.getCredential().avatarUrl.takeIf { it.isNotBlank() }
    }

    private fun maskAccount(id: String): String {
        if (id.length <= 4) return id
        return id.take(4) + "***"
    }

    fun isLoggedIn(provider: Provider = selectedProvider): Boolean = when (provider) {
        Provider.QQ -> QqCredentialManager.hasValidCredential()
        Provider.NETEASE -> NeteaseCredentialManager.hasValidCredential()
    }

    @SubscribeEvent
    fun onMainLoop(@Suppress("unused") event: MainLoopEvent) {
        pollLogin()
    }

    private fun pollLogin() {
        val provider = loginProvider ?: return
        if (qrBytes == null || System.currentTimeMillis() - lastLoginPoll < 2_000L) return
        if (loginFuture?.isDone == false) return
        lastLoginPoll = System.currentTimeMillis()
        loginFuture = when (provider) {
            Provider.QQ -> QqLoginService.pollLogin(qqQrSig).whenComplete { state, throwable ->
                Minecraft.getInstance().execute {
                    if (loginProvider != provider) return@execute
                    handleLoginState(provider, state?.name, throwable)
                }
            }

            Provider.NETEASE -> NeteaseLoginService.pollLogin(neteaseUniKey).whenComplete { state, throwable ->
                Minecraft.getInstance().execute {
                    if (loginProvider != provider) return@execute
                    handleLoginState(provider, state?.name, throwable)
                }
            }
        }
    }

    fun shareCurrentTrack() {
        val backend = MusicPlayerBackend.getInstance()
        val info = backend.currentMusicInfo
        if (info == null || info.provider == "local" || info.externalId.isBlank()) {
            status = "当前曲目无法同步"
            bumpRevision()
            return
        }
        MisakaNetworkClient.send(
            MusicSyncPackets.SharePacket(
                MusicSyncPackets.TrackSnapshot(
                    info.provider,
                    info.externalId,
                    info.name,
                    info.subtitle,
                    info.durationSeconds,
                    info.vip,
                    info.artworkUrl,
                    backend.currentTime,
                    backend.isPlaying
                )
            )
        )
        status = "已向附近玩家同步：${info.name}"
        bumpRevision()
    }

    @SubscribePacket
    fun receiveSync(packet: MusicSyncPackets.SyncPacket) {
        val snapshot = packet.snapshot()
        val provider = runCatching { Provider.valueOf(snapshot.provider().uppercase()) }.getOrNull() ?: return
        val entry = SearchEntry(
            provider,
            snapshot.trackId(),
            snapshot.title(),
            snapshot.artist(),
            snapshot.durationSeconds(),
            snapshot.vip(),
            snapshot.artworkUrl()
        )
        add(entry, false)
        if (snapshot.playing()) {
            cache(provider, entry.id).whenComplete { _, throwable ->
                if (throwable != null) return@whenComplete
                Minecraft.getInstance().execute {
                    val info = createMusicInfo(entry.toStoredTrack())
                    val backend = MusicPlayerBackend.getInstance()
                    backend.addOnlineTrack(info, true)
                    if (snapshot.durationSeconds() > 0) {
                        val ratio = snapshot.positionSeconds() / snapshot.durationSeconds().toFloat()
                        backend.seek(ratio.coerceIn(0f, 1f))
                    }
                }
            }
        }
        status = "已接收 ${packet.senderName()} 的同步播放"
        bumpRevision()
    }

    private fun handleLoginState(provider: Provider, state: String?, throwable: Throwable?) {
        if (throwable != null) {
            loginState = LoginState.FAILED
            status = "${providerLabel(provider)}登录失败：${rootMessage(throwable)}"
        } else when (state) {
            "SUCCESS" -> {
                qrBytes = null
                loginProvider = null
                loginFuture = null
                loginState = LoginState.IDLE
                status = "${providerLabel(provider)}：登录成功"
            }

            "EXPIRED" -> {
                qrBytes = null
                loginProvider = null
                loginFuture = null
                loginState = LoginState.EXPIRED
                status = "${providerLabel(provider)}：二维码已过期"
            }

            "FAILED" -> {
                loginState = LoginState.FAILED
                status = "${providerLabel(provider)}：登录失败"
            }

            else -> {
                loginState = LoginState.WAITING
                status = "${providerLabel(provider)}：等待扫码确认"
            }
        }
        bumpRevision()
    }

    private fun cache(provider: Provider, id: String): CompletableFuture<ByteBuffer> = when (provider) {
        Provider.QQ -> QqAudioCache.ensureCachedAsync(id)
        Provider.NETEASE -> NeteaseAudioCache.ensureCachedAsync(id)
    }

    fun providerByName(name: String): Provider? =
        Provider.entries.firstOrNull { it.storageName == name }

    fun ensureCached(provider: Provider, id: String): CompletableFuture<ByteBuffer> = cache(provider, id)

    /**
     * 播放回退链：先走玩家本地凭证缓存；失败（VIP/无权限）且服务器开启共享账号时，
     * 经服务器解析代理拿直链下载并收录缓存；共享账号不可用时优先报本地失败原因喵。
     */
    fun ensurePlayable(provider: Provider, id: String): CompletableFuture<ByteBuffer> {
        return cache(provider, id).exceptionallyCompose { localFailure ->
            resolveViaServer(provider, id).exceptionally { _ -> throw localFailure }
        }
    }

    private fun resolveViaServer(provider: Provider, id: String): CompletableFuture<ByteBuffer> {
        return org.academy.internal.client.app.music.session.SharedAccountClient
            .resolveTrack(provider.storageName, id)
            .thenApplyAsync({ urls ->
                if (urls.isEmpty()) throw IOException("resolve:no_url")
                val api = MusicProviders.byName(provider.storageName).orElseThrow()
                var failure: Exception? = null
                for (url in urls) {
                    try {
                        val bytes = HttpUtil.downloadBytes(url, api.userAgent(), api.referer(), 15000, 30000)
                        if (!AudioFormat.detect(bytes).isSupported) {
                            throw IOException("服务器解析返回不支持的音频格式")
                        }
                        return@thenApplyAsync when (provider) {
                            Provider.QQ -> QqAudioCache.acceptServerResolved(id, bytes)
                            Provider.NETEASE -> NeteaseAudioCache.acceptServerResolved(id, bytes)
                        }.join()
                    } catch (exception: Exception) {
                        failure = exception
                    }
                }
                throw failure ?: IOException("resolve:download_failed")
            }, AcademyCraft.executorService)
    }

    /**
     * 将共享曲目描述转换为可播放的 MusicInfo（懒加载缓存源，带共享账号回退），供共享播放会话使用喵。
     * 资源包曲目（provider=local）直接按资源定位符解析，无需下载。
     */
    fun toMusicInfo(entry: org.academy.internal.common.music.SharedTrackEntry): MusicInfo? {
        if (entry.provider() == "local") {
            val location = runCatching { Identifier.parse(entry.trackId()) }.getOrNull() ?: return null
            return MusicInfo(
                R.textures.gui.app.music.icon,
                MusicSource.fromIdentifier(location),
                entry.title(),
                entry.artist(),
                "local",
                entry.trackId(),
                entry.durationSeconds()
            )
        }
        val provider = providerByName(entry.provider()) ?: return null
        val source = MusicSource.fromSupplier {
            try {
                ensurePlayable(provider, entry.trackId()).join().duplicate()
            } catch (exception: Exception) {
                throw IOException(rootMessage(exception))
            }
        }
        return MusicInfo(
            R.textures.gui.app.music.icon,
            source,
            entry.title(),
            entry.artist(),
            entry.provider(),
            entry.trackId(),
            entry.durationSeconds(),
            entry.vip(),
            entry.artworkUrl()
        )
    }

    private fun playerCredential(provider: Provider): ProviderCredential = when (provider) {
        Provider.QQ -> {
            val credential = QqCredentialManager.getCredential()
            if (credential != null && credential.musicId.isNotBlank() && credential.musicKey.isNotBlank()) {
                val expiresAt = if (credential.keyExpiresIn > 0) {
                    credential.musicKeyCreateTime + credential.keyExpiresIn
                } else 0L
                ProviderCredential.ofQq(credential.musicId, credential.musicKey, expiresAt)
            } else ProviderCredential.ANONYMOUS
        }

        Provider.NETEASE -> ProviderCredential.fromCookieSupplier(NeteaseCredentialManager::getEffectiveCookie)
    }

    private fun createMusicInfo(track: StoredTrack): MusicInfo {
        val provider = Provider.entries.firstOrNull { it.storageName == track.provider } ?: Provider.QQ
        val source = MusicSource.fromSupplier {
            try {
                cache(provider, track.id).join().duplicate()
            } catch (exception: Exception) {
                throw IOException(rootMessage(exception))
            }
        }
        return MusicInfo(
            R.textures.gui.app.music.icon,
            source,
            track.title,
            track.artist,
            track.provider,
            track.id,
            track.durationSeconds,
            track.vip,
            track.artworkUrl
        )
    }

    private fun loadPlaylist() {
        if (!Files.exists(storageFile)) return
        runCatching {
            Files.newBufferedReader(storageFile, StandardCharsets.UTF_8).use { reader ->
                val type = object : TypeToken<List<StoredTrack>>() {}.type
                val loaded = gson.fromJson<List<StoredTrack>>(reader, type).orEmpty()
                synchronized(storedTracks) {
                    storedTracks.clear()
                    storedTracks.addAll(loaded.filter { it.id.isNotBlank() && it.provider.isNotBlank() })
                }
                MusicPlayerBackend.getInstance().replaceOnlineTracks(storedTracks.map(::createMusicInfo))
            }
        }.onFailure { AcademyCraft.LOGGER.warn("Failed to load online music playlist", it) }
    }

    private fun savePlaylist() {
        runCatching {
            Files.createDirectories(storageFile.parent)
            Files.newBufferedWriter(storageFile, StandardCharsets.UTF_8).use { writer ->
                gson.toJson(storedTracks, writer)
            }
        }.onFailure { AcademyCraft.LOGGER.warn("Failed to save online music playlist", it) }
    }

    private fun SearchEntry.toStoredTrack() = StoredTrack(
        provider.storageName,
        id,
        title,
        artist,
        durationSeconds,
        vip,
        artworkUrl
    )

    private fun providerLabel(provider: Provider) = when (provider) {
        Provider.QQ -> "QQ 音乐"
        Provider.NETEASE -> "网易云音乐"
    }

    private fun rootMessage(throwable: Throwable?): String {
        var current = throwable ?: return "未知错误"
        while (current.cause != null) current = current.cause!!
        return current.message?.takeIf(String::isNotBlank) ?: current.javaClass.simpleName
    }
}
