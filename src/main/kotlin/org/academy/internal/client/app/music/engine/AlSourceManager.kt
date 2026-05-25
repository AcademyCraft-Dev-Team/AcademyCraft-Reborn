package org.academy.internal.client.app.music.engine

import org.lwjgl.openal.AL11
import java.nio.ByteBuffer
import java.util.*

class AlSourceManager {
    private var alSource = -1
    val bufferIds: IntArray = IntArray(BUFFER_COUNT)
    private var volume = 1.0f

    fun initialize() {
        if (isValid) return
        alSource = AL11.alGenSources()
        AL11.alGenBuffers(bufferIds)
        AL11.alSourcef(alSource, AL11.AL_GAIN, volume)
    }

    fun destroy() {
        if (!isValid) return
        stop()
        AL11.alDeleteBuffers(bufferIds)
        AL11.alDeleteSources(alSource)
        alSource = -1
        Arrays.fill(bufferIds, 0)
    }

    fun play() {
        if (isValid) AL11.alSourcePlay(alSource)
    }

    fun pause() {
        if (isValid) AL11.alSourcePause(alSource)
    }

    fun stop() {
        if (isValid) {
            AL11.alSourceStop(alSource)
            resetBufferQueue()
        }
    }

    fun resetBufferQueue() {
        if (isValid) AL11.alSourcei(alSource, AL11.AL_BUFFER, 0)
    }

    fun queueBuffer(bufferId: Int, format: Int, data: ByteBuffer, sampleRate: Int) {
        if (isValid) {
            AL11.alBufferData(bufferId, format, data, sampleRate)
            AL11.alSourceQueueBuffers(alSource, bufferId)
        }
    }

    fun unqueueSingleProcessedBuffer(): Int {
        if (!isValid || processedBufferCount == 0) return 0
        return AL11.alSourceUnqueueBuffers(alSource)
    }

    val processedBufferCount: Int
        get() = if (isValid) AL11.alGetSourcei(alSource, AL11.AL_BUFFERS_PROCESSED) else 0

    val queuedBufferCount: Int
        get() = if (isValid) AL11.alGetSourcei(alSource, AL11.AL_BUFFERS_QUEUED) else 0

    fun getBufferSize(bufferId: Int): Int {
        return if (isValid) AL11.alGetBufferi(bufferId, AL11.AL_SIZE) else 0
    }

    val currentTimeOffset: Float
        get() = if (isValid) AL11.alGetSourcef(alSource, AL11.AL_SEC_OFFSET) else 0f

    fun setVolume(value: Float) {
        volume = value
        if (isValid) AL11.alSourcef(alSource, AL11.AL_GAIN, volume)
    }

    val isValid: Boolean
        get() = alSource != -1 && AL11.alIsSource(alSource)

    val isPlaying: Boolean
        get() = if (isValid) AL11.alGetSourcei(alSource, AL11.AL_SOURCE_STATE) == AL11.AL_PLAYING else false

    companion object {
        private const val BUFFER_COUNT = 4
    }
}