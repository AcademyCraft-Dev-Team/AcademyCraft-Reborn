package org.academy.internal.client.app.music.backend

import org.academy.internal.client.app.music.common.PlaybackMode
import org.academy.internal.client.app.music.data.MusicInfo
import java.util.*

class PlaylistManager {
    private val playlist: MutableList<MusicInfo> = ArrayList<MusicInfo>()
    private val shuffledPlaylist: MutableList<Int> = ArrayList<Int>()

    private var currentTrackIndex = -1
    private var shuffleIndex = -1
    var playbackMode: PlaybackMode = PlaybackMode.REPEAT_LIST
        private set

    fun updatePlaylist(newPlaylist: MutableList<MusicInfo>) {
        playlist.clear()
        playlist.addAll(newPlaylist)
        shuffledPlaylist.clear()
        currentTrackIndex = -1
        shuffleIndex = -1
    }

    fun nextTrackIndex(): Int {
        if (playlist.isEmpty()) return -1

        return when (playbackMode) {
            PlaybackMode.REPEAT_ONE -> currentTrackIndex
            PlaybackMode.SHUFFLE -> {
                if (shuffledPlaylist.isEmpty()) {
                    generateShuffledPlaylist()
                } else {
                    shuffleIndex++
                    if (shuffleIndex >= shuffledPlaylist.size) {
                        generateShuffledPlaylist(keepCurrentFirst = false)
                        if (shuffledPlaylist.size > 1) shuffleIndex = 1
                    }
                }
                if (shuffledPlaylist.isEmpty()) -1 else shuffledPlaylist[shuffleIndex]
            }

            PlaybackMode.REPEAT_LIST -> (currentTrackIndex + 1) % playlist.size
        }
    }

    fun previousTrackIndex(): Int {
        if (playlist.isEmpty()) return -1

        return when (playbackMode) {
            PlaybackMode.REPEAT_ONE -> currentTrackIndex
            PlaybackMode.SHUFFLE -> {
                if (shuffledPlaylist.isEmpty()) {
                    generateShuffledPlaylist()
                    if (shuffledPlaylist.isEmpty()) return -1
                    shuffleIndex = shuffledPlaylist.size - 1
                    return shuffledPlaylist[shuffleIndex]
                }
                shuffleIndex--
                if (shuffleIndex < 0) shuffleIndex = shuffledPlaylist.size - 1
                shuffledPlaylist[shuffleIndex]
            }

            PlaybackMode.REPEAT_LIST -> (currentTrackIndex - 1 + playlist.size) % playlist.size
        }
    }

    private fun generateShuffledPlaylist(keepCurrentFirst: Boolean = true) {
        shuffledPlaylist.clear()
        if (playlist.isEmpty()) return
        for (i in playlist.indices) shuffledPlaylist.add(i)
        shuffledPlaylist.shuffle(Random())
        if (keepCurrentFirst && currentTrackIndex != -1) {
            val currentItemPos = shuffledPlaylist.indexOf(currentTrackIndex)
            if (currentItemPos != -1) Collections.swap(shuffledPlaylist, 0, currentItemPos)
        }
        shuffleIndex = 0
    }

    fun cyclePlaybackMode() {
        playbackMode = PlaybackMode.entries[(playbackMode.ordinal + 1) % PlaybackMode.entries.size]
        if (playbackMode == PlaybackMode.SHUFFLE && (shuffledPlaylist.isEmpty() || currentTrackIndex == -1)) {
            generateShuffledPlaylist()
        }
    }

    fun getTrack(index: Int): Optional<MusicInfo> {
        if (index < 0 || index >= playlist.size) return Optional.empty<MusicInfo>()
        return Optional.of<MusicInfo>(playlist[index])
    }

    fun getPlaylist(): MutableList<MusicInfo> {
        return Collections.unmodifiableList(playlist)
    }

    fun getCurrentTrackIndex(): Int {
        return currentTrackIndex
    }

    fun setCurrentTrackIndex(currentTrackIndex: Int) {
        this.currentTrackIndex = currentTrackIndex
        if (playbackMode == PlaybackMode.SHUFFLE) {
            val newShuffleIndex = shuffledPlaylist.indexOf(currentTrackIndex)
            if (newShuffleIndex != -1) {
                shuffleIndex = newShuffleIndex
            } else if (!shuffledPlaylist.isEmpty()) generateShuffledPlaylist()
        }
    }
}