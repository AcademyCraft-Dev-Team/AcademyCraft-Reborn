package org.academy.internal.client.app.music.engine

import org.academy.internal.client.app.music.common.PlaybackState
import org.academy.internal.client.app.music.decoder.DecoderFactory
import org.academy.internal.client.app.music.decoder.DecoderThread
import org.lwjgl.system.MemoryUtil
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class StreamingPlaybackSession private constructor(
    private val alPlayer: AlSourceManager,
    private val decoderRunnable: DecoderThread,
    private val decoderThread: Thread,
    private val decodedDataQueue: BlockingQueue<Any>,
    private val bufferStreamer: BufferStreamer,
    val sampleRate: Int,
    val totalSamples: Long,
    private val desiredState: AtomicReference<PlaybackState>
) {
    private val shutdownDecoder = AtomicBoolean(false)

    val currentTime: Float get() = bufferStreamer.getCurrentTime(sampleRate)
    val totalDuration: Float get() = if (sampleRate > 0) totalSamples.toFloat() / sampleRate else 0f
    val isFinished: Boolean get() = bufferStreamer.isFinished

    fun seek(frame: Long) {
        bufferStreamer.seek(frame)
        decoderRunnable.seek(frame)
    }

    fun destroy() {
        shutdownDecoder.set(true)
        decoderThread.interrupt()
        try {
            decoderThread.join(1000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        alPlayer.destroy()

        decodedDataQueue.forEach { if (it is ByteBuffer) MemoryUtil.memFree(it) }
        decodedDataQueue.clear()
    }

    fun update() {
        bufferStreamer.update()

        when (desiredState.get()) {
            PlaybackState.PLAYING -> if (bufferStreamer.isPrimed && !alPlayer.isPlaying) alPlayer.play()
            PlaybackState.PAUSED -> if (alPlayer.isPlaying) alPlayer.pause()
            PlaybackState.IDLE -> if (alPlayer.isPlaying) alPlayer.pause()
        }
    }

    fun setVolume(value: Float) {
        alPlayer.setVolume(value)
    }

    companion object {
        private const val QUEUE_CAPACITY = 8

        fun create(
            audioData: ByteBuffer,
            startFrame: Long,
            desiredState: AtomicReference<PlaybackState>,
            volume: Float
        ): StreamingPlaybackSession {
            val stream = DecoderFactory.create(audioData) ?: throw IOException("Failed to create audio stream")
            val sampleRate = stream.sampleRate
            val totalSamples = stream.totalSamples

            val alPlayer = AlSourceManager().apply {
                initialize()
                setVolume(volume)
            }

            val decodedDataQueue: BlockingQueue<Any> = ArrayBlockingQueue(QUEUE_CAPACITY)
            val bufferStreamer = BufferStreamer(alPlayer, decodedDataQueue, stream.channels, startFrame, sampleRate)
            val decoderRunnable = DecoderThread(stream, decodedDataQueue, AtomicBoolean(false), startFrame)
            val decoderThread = Thread(decoderRunnable, "AC-Music-Decoder").apply {
                isDaemon = true
                start()
            }

            return StreamingPlaybackSession(
                alPlayer, decoderRunnable, decoderThread, decodedDataQueue,
                bufferStreamer, sampleRate, totalSamples, desiredState
            )
        }
    }
}