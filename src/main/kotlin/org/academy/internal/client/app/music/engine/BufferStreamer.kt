package org.academy.internal.client.app.music.engine

import org.academy.internal.client.app.music.decoder.DecoderThread
import org.lwjgl.openal.AL11
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.concurrent.BlockingQueue

internal class BufferStreamer(
    private val alPlayer: AlSourceManager,
    private val decodedDataQueue: BlockingQueue<Any>,
    private val channels: Int,
    startFrame: Long,
    private val sampleRate: Int
) {
    var isPrimed = false
        private set
    var streamReadFinished = false
        private set
    private var baseSampleOffset = startFrame
    private var seeking = false
    private var nextBufferIndex = 0

    val isFinished: Boolean get() = streamReadFinished && alPlayer.queuedBufferCount == 0

    fun seek(frame: Long) {
        alPlayer.stop()
        alPlayer.resetBufferQueue()
        isPrimed = false
        streamReadFinished = false
        baseSampleOffset = frame
        seeking = true
        nextBufferIndex = 0
    }

    fun update() {
        if (isFinished) return
        if (!isPrimed) primeInitialBuffers() else streamNextBuffers()
    }

    private fun primeInitialBuffers() {
        while (nextBufferIndex < alPlayer.bufferIds.size) {
            val bufferId = alPlayer.bufferIds[nextBufferIndex]
            if (feedBuffer(bufferId)) nextBufferIndex++ else break
        }
        if (nextBufferIndex == alPlayer.bufferIds.size) isPrimed = true
    }

    private fun streamNextBuffers() {
        repeat(alPlayer.processedBufferCount) {
            val bufferId = alPlayer.unqueueSingleProcessedBuffer()
            if (bufferId != 0) {
                updateSampleOffset(bufferId)
                feedBuffer(bufferId)
            }
        }
    }

    private fun feedBuffer(bufferId: Int): Boolean {
        val item = decodedDataQueue.poll() ?: return false

        when (item) {
            is DecoderThread.SeekComplete -> {
                seeking = false
                return false
            }

            is DecoderThread.PoisonPill -> {
                streamReadFinished = true
                return false
            }

            is ByteBuffer -> {
                if (seeking) {
                    MemoryUtil.memFree(item)
                    return false
                }

                val format = if (channels == 1) AL11.AL_FORMAT_MONO16 else AL11.AL_FORMAT_STEREO16
                alPlayer.queueBuffer(bufferId, format, item, sampleRate)
                MemoryUtil.memFree(item)
                return true
            }

            else -> return false
        }
    }

    private fun updateSampleOffset(bufferId: Int) {
        if (channels > 0) {
            val bytesInBuf = alPlayer.getBufferSize(bufferId)
            val framesInBuf = bytesInBuf / (channels * 2)
            baseSampleOffset += framesInBuf
        }
    }

    fun getCurrentTime(streamSampleRate: Int): Float {
        if (streamSampleRate == 0) return 0f
        val alOffset = alPlayer.currentTimeOffset
        return (baseSampleOffset.toFloat() / streamSampleRate) + alOffset
    }
}