package org.academy.internal.client.app.music.session

import net.minecraft.client.Minecraft
import org.academy.AcademyCraft
import org.academy.internal.client.app.music.backend.MusicPlayerBackend
import org.academy.internal.client.app.music.backend.OnlineMusicManager
import org.academy.internal.common.music.PlaybackTimeline
import org.academy.internal.common.music.SharedTrackEntry
import kotlin.math.abs

/**
 * 服务端权威时间线的通用跟随器：落轨到指定秒、暂停/续播/跳转对齐、周期性漂移纠正。
 * 音乐室与全服点播客户端会话共用同一套对齐逻辑喵。
 *
 * 关键不变量：曲内进度以"服务端锚点换算出的秒数"为基准，本机接收时刻只用于推进增量。
 * 服务端广播时不会重新锚定时间线（anchorPositionSeconds 仍是起播时的值），
 * 若把本机接收时刻直接当作锚点起点，会把中途加入的曲目误算成 0 秒并从头重播喵。
 */
class SharedPlaybackFollower {
    @Volatile
    private var timeline: PlaybackTimeline? = null

    @Volatile
    private var receivedAtMillis: Long = 0L

    /** 接收该时间线时由服务端锚点换算出的曲内秒，作为本机推进的基准喵。 */
    @Volatile
    private var basePositionSeconds: Float = 0f

    @Volatile
    private var appliedKey: String? = null

    @Volatile
    private var lastDriftCheckMillis: Long = 0L

    val currentTrackId: String?
        get() = timeline?.entry()?.trackId()

    val hasTimeline: Boolean
        get() = timeline != null

    fun expectedPlaying(): Boolean = timeline?.playing() == true

    /**
     * 依据最近一次同步推算当前应处的播放位置（秒），供 UI 进度条渲染与落轨对齐喵。
     */
    fun expectedPositionSeconds(): Float {
        val current = timeline ?: return 0f
        val elapsed = if (current.playing()) (System.currentTimeMillis() - receivedAtMillis) / 1000f else 0f
        val position = basePositionSeconds + elapsed
        val duration = current.entry().durationSeconds().toFloat()
        return if (duration > 0f) position.coerceIn(0f, duration) else position.coerceAtLeast(0f)
    }

    /**
     * 应用新的权威时间线：新曲目落轨到指定秒；同曲目仅做暂停/续播与进度对齐；null 表示停止喵。
     */
    fun apply(newTimeline: PlaybackTimeline?, serverGameTime: Long) {
        val backend = MusicPlayerBackend.getInstance()
        if (newTimeline == null) {
            appliedKey = null
            timeline = null
            basePositionSeconds = 0f
            backend.stop()
            return
        }
        timeline = newTimeline
        receivedAtMillis = System.currentTimeMillis()
        basePositionSeconds = newTimeline.expectedPositionSeconds(serverGameTime)

        val entry = newTimeline.entry()
        val sameTrack = appliedKey?.startsWith(trackPrefix(entry)) == true
        appliedKey = timelineKey(entry, newTimeline)

        if (sameTrack && hasLoaded(backend, entry)) {
            alignExisting(backend, entry)
            return
        }
        loadAndPlay(entry)
    }

    /**
     * 周期性漂移纠正：本地进度与推算进度偏差超过阈值时跳转对齐喵。
     */
    fun correctDrift() {
        val current = timeline ?: return
        if (!current.playing()) return
        val now = System.currentTimeMillis()
        if (now - lastDriftCheckMillis < DRIFT_CHECK_INTERVAL_MILLIS) return
        lastDriftCheckMillis = now

        val backend = MusicPlayerBackend.getInstance()
        if (!backend.isPlaying) return
        val entry = current.entry()
        if (backend.currentMusicInfo?.externalId != entry.trackId()) return

        val expected = expectedPositionSeconds()
        val actual = backend.currentTime
        if (abs(expected - actual) > DRIFT_CORRECTION_THRESHOLD_SECONDS) {
            AcademyCraft.LOGGER.debug(
                "Shared playback drift corrected: expected={} actual={}", expected, actual
            )
            backend.seekToSeconds(expected)
        }
    }

    fun reset() {
        timeline = null
        appliedKey = null
        basePositionSeconds = 0f
    }

    private fun loadAndPlay(entry: SharedTrackEntry) {
        val backend = MusicPlayerBackend.getInstance()
        // 已装载同一曲目时只做对齐，绝不重新解码/从头播放喵.
        if (hasLoaded(backend, entry)) {
            alignExisting(backend, entry)
            return
        }
        if (entry.provider() == "local") {
            // 资源包曲目：本机装有同一资源包时列表已存在，直接落轨；
            // 否则登记为待播曲目，由解码失败上报（无该资源包自然无法播放）喵.
            val known = backend.playlist.any {
                it.provider == "local" && it.externalId == entry.trackId()
            }
            if (!known) {
                val info = OnlineMusicManager.toMusicInfo(entry)
                if (info == null) {
                    appliedKey = null
                    return
                }
                backend.addOnlineTrack(info, false)
            }
            Minecraft.getInstance().execute {
                if (!stillWanted(entry)) return@execute
                startFromLatestTimeline(backend, entry)
            }
            return
        }
        val info = OnlineMusicManager.toMusicInfo(entry)
        if (info == null) {
            appliedKey = null
            return
        }
        backend.addOnlineTrack(info, false)
        val provider = OnlineMusicManager.providerByName(entry.provider())
        if (provider == null) {
            appliedKey = null
            return
        }
        // 走 ensurePlayable 回退链：本地凭证失败时经服务器共享账号解析，保证无凭证的订阅者也能播喵.
        OnlineMusicManager.ensurePlayable(provider, entry.trackId()).whenComplete { _, throwable ->
            Minecraft.getInstance().execute {
                // 按曲目守卫而非完整 key: 下载期间的心跳会刷新 appliedKey,
                // 用 key 比较会让下载完成后回调被丢弃, 导致该曲目永远无声喵.
                if (!stillWanted(entry)) return@execute
                if (throwable != null) {
                    OnlineMusicManager.onPlayError("曲目缓存失败：${throwable.message ?: "未知错误"}")
                    return@execute
                }
                // 下载期间曲目可能已被重新装载或手动开播，这里再判一次避免从头重播喵.
                val loaded = hasLoaded(backend, entry)
                if (loaded) {
                    alignExisting(backend, entry)
                } else {
                    startFromLatestTimeline(backend, entry)
                }
            }
        }
    }

    /**
     * 对已装载的同曲目做暂停/续播与进度对齐（不重新解码）喵。
     */
    private fun alignExisting(backend: MusicPlayerBackend, entry: SharedTrackEntry) {
        if (!hasLoaded(backend, entry)) return
        val playing = timeline?.playing() == true
        if (playing && !backend.isPlaying) {
            backend.resume()
        } else if (!playing && backend.isPlaying) {
            backend.pause()
        }
        val target = expectedPositionSeconds()
        if (abs(backend.currentTime - target) > SYNC_SEEK_THRESHOLD_SECONDS) {
            backend.seekToSeconds(target)
        }
    }

    /**
     * 本机当前装载的曲目是否就是该共享曲目喵。
     */
    private fun hasLoaded(backend: MusicPlayerBackend, entry: SharedTrackEntry): Boolean {
        val current = backend.currentMusicInfo ?: return false
        return current.provider == entry.provider && current.externalId == entry.trackId()
    }

    /**
     * 当前权威时间线是否仍指向该曲目（异步落轨期间可能已被切歌）喵。
     */
    private fun stillWanted(entry: SharedTrackEntry): Boolean {
        val current = timeline ?: return false
        val active = current.entry()
        return active.provider() == entry.provider() && active.trackId() == entry.trackId()
    }

    /**
     * 用最新时间线定位起播秒与暂停态，避免使用下载发起时的过期快照喵。
     */
    private fun startFromLatestTimeline(backend: MusicPlayerBackend, entry: SharedTrackEntry) {
        val current = timeline ?: return
        backend.playAt(
            entry.provider(),
            entry.trackId(),
            expectedPositionSeconds(),
            !current.playing()
        )
    }

    private fun timelineKey(entry: SharedTrackEntry, timeline: PlaybackTimeline): String {
        return "${entry.provider()}:${entry.trackId()}:" +
                "${timeline.anchorGameTime()}:${timeline.playing()}:${timeline.anchorPositionSeconds()}"
    }

    private fun trackPrefix(entry: SharedTrackEntry): String = "${entry.provider()}:${entry.trackId()}:"

    companion object {
        private const val DRIFT_CHECK_INTERVAL_MILLIS = 5000L
        private const val DRIFT_CORRECTION_THRESHOLD_SECONDS = 1.5f
        private const val SYNC_SEEK_THRESHOLD_SECONDS = 2.0f
    }
}
