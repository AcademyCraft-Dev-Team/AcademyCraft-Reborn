package org.academy.internal.client.app.music.engine

import org.academy.internal.client.app.music.common.PlaybackState
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

class AudioPlayer {
    private var session: StreamingPlaybackSession? = null
    private val desiredState = AtomicReference(PlaybackState.IDLE)
    var volume: Float = 1.0f
        set(value) {
            field = value
            session?.setVolume(value)
        }

    val state: PlaybackState get() = desiredState.get()
    val currentTime: Float get() = session?.currentTime ?: 0f
    val totalDuration: Float get() = session?.totalDuration ?: 0f
    val isFinished: Boolean get() = session?.isFinished ?: true
    val hasSession: Boolean get() = session != null

    fun play(audioData: ByteBuffer, startFrame: Long) {
        stop()
        desiredState.set(PlaybackState.PLAYING)
        createSession(audioData, startFrame)
    }

    fun pause() {
        if (desiredState.get() == PlaybackState.PLAYING) desiredState.set(PlaybackState.PAUSED)
    }

    fun resume() {
        if (desiredState.get() == PlaybackState.PAUSED || (desiredState.get() == PlaybackState.IDLE && hasSession)) {
            desiredState.set(PlaybackState.PLAYING)
        }
    }

    fun togglePlayPause() {
        when (desiredState.get()) {
            PlaybackState.PLAYING -> pause()
            PlaybackState.PAUSED -> resume()
            PlaybackState.IDLE -> if (hasSession) resume()
        }
    }

    fun stop() {
        desiredState.set(PlaybackState.IDLE)
        session?.destroy()
        session = null
    }

    fun seek(audioData: ByteBuffer, timeRatio: Float) {
        val clampedRatio = timeRatio.coerceIn(0f, 1f)
        val session = session
        if (session != null) {
            val totalSamples = session.totalSamples
            val targetFrame = (clampedRatio * totalSamples).toLong()
            session.seek(targetFrame)
        } else {
            stop()
            desiredState.set(PlaybackState.IDLE)
            val newSession = createSession(audioData, 0L)
            val totalSamples = newSession.totalSamples
            val targetFrame = (clampedRatio * totalSamples).toLong()
            newSession.seek(targetFrame)
        }
    }

    fun update() {
        session?.let {
            it.update()
            if (it.isFinished) stop()
        }
    }

    private fun createSession(audioData: ByteBuffer, startFrame: Long): StreamingPlaybackSession {
        val newSession = StreamingPlaybackSession.create(audioData, startFrame, desiredState, volume)
        session = newSession
        return newSession
    }
}