package org.academy.internal.client.app.music.backend

import net.minecraft.client.Minecraft
import net.minecraft.client.sounds.SoundEngineExecutor
import net.minecraft.resources.Identifier
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientPauseChangeEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.common.NeoForge
import org.academy.AcademyCraft
import org.academy.api.client.gui.msdf.font.MsdfFont
import org.academy.api.client.gui.msdf.font.MsdfFontService
import org.academy.api.client.vanilla.MainLoopEvent
import org.academy.internal.client.app.music.common.PlaybackMode
import org.academy.internal.client.app.music.common.PlaybackState
import org.academy.internal.client.app.music.data.MusicData
import org.academy.internal.client.app.music.data.MusicInfo
import org.academy.internal.client.app.music.data.MusicSource
import org.academy.internal.client.app.music.engine.AudioPlayer
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.stream.Collectors

class MusicPlayerBackend private constructor() {
    private val audioPlayer = AudioPlayer()
    private val playlistManager = PlaylistManager()
    private val resourceTracks = mutableListOf<MusicInfo>()
    private val onlineTracks = mutableListOf<MusicInfo>()
    private val playlistRevisionCounter = AtomicInteger()

    private var currentTrackData: ByteBuffer? = null
    private val isSessionActive = AtomicBoolean(false)
    private val resumeAfterGamePause = AtomicBoolean(false)

    private val soundExecutor: SoundEngineExecutor
        get() = Minecraft.getInstance().soundManager.soundEngine.executor

    private fun runOnSoundEngine(task: Runnable) {
        soundExecutor.execute(task)
    }

    @SubscribeEvent
    fun onClientPauseChangePost(event: ClientPauseChangeEvent.Post) {
        runOnSoundEngine {
            if (event.isPaused) {
                val wasPlaying = audioPlayer.state == PlaybackState.PLAYING
                resumeAfterGamePause.set(wasPlaying)
                if (wasPlaying) audioPlayer.pause()
            } else if (resumeAfterGamePause.getAndSet(false)
                && audioPlayer.state == PlaybackState.PAUSED
            ) {
                audioPlayer.resume()
            }
        }
    }

    @SubscribeEvent
    fun onLoggingOut(@Suppress("unused") event: ClientPlayerNetworkEvent.LoggingOut) {
        // Disable auto-advance immediately so an already queued main-loop update
        // cannot start another track while the world is being torn down.
        isSessionActive.set(false)
        resumeAfterGamePause.set(false)
        runOnSoundEngine { performStop() }
    }

    @SubscribeEvent
    fun onMainLoop(event: MainLoopEvent) {
        runOnSoundEngine { update() }
    }

    fun update() {
        audioPlayer.update()

        if (audioPlayer.state == PlaybackState.IDLE && isSessionActive.get()) {
            if (playbackMode == PlaybackMode.REPEAT_ONE) {
                val index = playlistManager.getCurrentTrackIndex()
                if (index != -1) performPlay(index)
            } else performPlayNext()
        }
    }

    fun handleContextReset() {
        runOnSoundEngine { performHandleContextReset() }
    }

    private fun performHandleContextReset() {
        isSessionActive.set(false)
        audioPlayer.stop()
        currentTrackData = null
    }

    fun updatePlaylistFromData(musicMap: MutableMap<String, MusicData>, sourceDescription: String) {
        val newPlaylist = musicMap.entries.stream()
            .map { entry -> parseMusicEntry(entry.key, entry.value, sourceDescription) }
            .filter { obj -> obj.isPresent }
            .map { obj -> obj.get() }
            .collect(Collectors.toList())

        // 初始化以缓解 MusicApp 第一次打开时卡顿喵
        for (info in newPlaylist) {
            val codePoints = info.name.codePoints().toArray()
            for (cp in codePoints) {
                val font: MsdfFont = MsdfFontService.getFont(cp)
                font.getGlyph(cp)
            }
            Minecraft.getInstance().textureManager.getTexture(info.icon)
        }

        runOnSoundEngine {
            performStop()
            resourceTracks.clear()
            resourceTracks.addAll(newPlaylist)
            rebuildPlaylist()
        }
    }

    fun addOnlineTrack(info: MusicInfo, playNow: Boolean = false) {
        runOnSoundEngine {
            val existingIndex = onlineTracks.indexOfFirst {
                it.provider == info.provider && it.externalId == info.externalId
            }
            if (existingIndex < 0) onlineTracks.add(info) else onlineTracks[existingIndex] = info
            rebuildPlaylist()
            if (playNow) {
                val index = playlistManager.getPlaylist().indexOf(info)
                if (index >= 0) performPlay(index)
            }
        }
    }

    fun replaceOnlineTracks(tracks: List<MusicInfo>) {
        runOnSoundEngine {
            onlineTracks.clear()
            onlineTracks.addAll(tracks.distinctBy { it.provider to it.externalId })
            rebuildPlaylist()
        }
    }

    fun removeOnlineTrack(provider: String, externalId: String) {
        runOnSoundEngine {
            val current = currentMusicInfo
            onlineTracks.removeIf { it.provider == provider && it.externalId == externalId }
            if (current?.provider == provider && current.externalId == externalId) performStop()
            rebuildPlaylist()
        }
    }

    private fun rebuildPlaylist() {
        val current = currentMusicInfo
        val rebuilt = (resourceTracks + onlineTracks).toMutableList()
        playlistManager.updatePlaylist(rebuilt)
        if (current != null) playlistManager.setCurrentTrackIndex(rebuilt.indexOf(current))
        playlistRevisionCounter.incrementAndGet()
    }

    private fun parseMusicEntry(name: String, data: MusicData, sourceDescription: String): Optional<MusicInfo> {
        try {
            val iconLocation = Identifier.parse(data.icon)
            val source = createMusicSource(data.sourceType, data.source)
            return Optional.of(MusicInfo(iconLocation, source, name, data.subtitle))
        } catch (e: Exception) {
            logger.error("Failed to parse music entry '{}' from {}: {}", name, sourceDescription, e.message)
            return Optional.empty()
        }
    }

    private fun createMusicSource(type: String, path: String): MusicSource {
        if (type.equals("RESOURCE_LOCATION", ignoreCase = true)) {
            return MusicSource.fromIdentifier(Identifier.parse(path))
        }
        if (type.equals("PATH", ignoreCase = true)) return MusicSource.fromAbsolutePath(path)
        throw IllegalArgumentException("Invalid source_type: $type")
    }

    fun play(info: MusicInfo?) {
        val index = playlistManager.getPlaylist().indexOf(info)
        if (index != -1) play(index)
    }

    fun play(trackIndex: Int) {
        runOnSoundEngine { performPlay(trackIndex) }
    }

    private fun performPlay(trackIndex: Int) {
        if (playlistManager.getCurrentTrackIndex() == trackIndex && audioPlayer.state == PlaybackState.PLAYING) return

        playlistManager.getTrack(trackIndex).ifPresentOrElse(Consumer { mediaInfo ->
            try {
                val data = mediaInfo.source.data
                currentTrackData = data
                playlistManager.setCurrentTrackIndex(trackIndex)
                audioPlayer.play(data, 0)
                isSessionActive.set(true)
            } catch (e: IOException) {
                logger.error("Failed to play media: {}", mediaInfo.name, e)
                performStop()
            }
        }) {
            logger.warn("Attempted to play track with invalid index: {}", trackIndex)
            performStop()
        }
    }

    fun stop() {
        runOnSoundEngine { this.performStop() }
    }

    private fun performStop() {
        isSessionActive.set(false)
        resumeAfterGamePause.set(false)
        playlistManager.setCurrentTrackIndex(-1)
        audioPlayer.stop()
        currentTrackData = null
    }

    fun togglePlayPause() {
        runOnSoundEngine { this.performTogglePlayPause() }
    }

    private fun performTogglePlayPause() {
        if (audioPlayer.state == PlaybackState.PLAYING || audioPlayer.state == PlaybackState.PAUSED) {
            audioPlayer.togglePlayPause()
        } else if (audioPlayer.hasSession) audioPlayer.resume()
        else {
            val trackIndex = playlistManager.getCurrentTrackIndex()
            if (trackIndex != -1) performPlay(trackIndex)
        }
    }

    fun playNext() {
        runOnSoundEngine { this.performPlayNext() }
    }

    private fun performPlayNext() {
        val nextIndex = playlistManager.nextTrackIndex()
        if (nextIndex != -1) performPlay(nextIndex)
        else performStop()
    }

    fun playPrevious() {
        runOnSoundEngine { this.performPlayPrevious() }
    }

    private fun performPlayPrevious() {
        val prevIndex = playlistManager.previousTrackIndex()
        if (prevIndex != -1) performPlay(prevIndex)
    }

    fun seek(timeRatio: Float) {
        runOnSoundEngine { performSeek(timeRatio) }
    }

    private fun performSeek(timeRatio: Float) {
        val data = currentTrackData ?: return

        if (audioPlayer.totalDuration > 0 && timeRatio >= 0.999f) {
            val currentIndex = playlistManager.getCurrentTrackIndex()
            val nextIndex = playlistManager.nextTrackIndex()
            performStop()
            if (playbackMode == PlaybackMode.REPEAT_ONE) {
                if (currentIndex != -1) performPlay(currentIndex)
            } else if (nextIndex != -1) performPlay(nextIndex)
        } else audioPlayer.seek(data, timeRatio)
    }

    fun cyclePlaybackMode() {
        playlistManager.cyclePlaybackMode()
    }

    val currentTime: Float get() = audioPlayer.currentTime
    val playlist: MutableList<MusicInfo> get() = playlistManager.getPlaylist()
    val isPlaying: Boolean get() = audioPlayer.state == PlaybackState.PLAYING
    val isPaused: Boolean get() = audioPlayer.state == PlaybackState.PAUSED
    val totalDuration: Float get() = audioPlayer.totalDuration
    val playbackMode: PlaybackMode get() = playlistManager.playbackMode
    val currentTrackIndex: Int get() = playlistManager.getCurrentTrackIndex()
    val playlistRevision: Int get() = playlistRevisionCounter.get()
    val currentMusicInfo: MusicInfo?
        get() = if (currentTrackIndex == -1) null else playlist[currentTrackIndex]

    var volume: Float
        get() = audioPlayer.volume
        set(value) = runOnSoundEngine { audioPlayer.volume = value }

    companion object {
        private val logger = AcademyCraft.getLogger()
        private var instance: MusicPlayerBackend? = null

        fun init() {
            if (instance == null) {
                AlbumArtworkCache.init()
                instance = MusicPlayerBackend()
                NeoForge.EVENT_BUS.register(instance)
                OnlineMusicManager.init()
            }
        }

        fun getInstance(): MusicPlayerBackend {
            return checkNotNull(instance) { "MusicPlayerBackend not initialized" }
        }
    }
}
