package org.academy.internal.client.app.music.decoder

import org.academy.AcademyCraft
import org.lwjgl.system.MemoryUtil
import org.slf4j.Logger
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class DecoderThread(
    private val stream: AudioStream,
    private val dataQueue: BlockingQueue<Any>,
    private val shutdown: AtomicBoolean,
    private val startFrame: Long
) : Runnable {
    private val pendingSeek = AtomicLong(-1)

    fun seek(frame: Long) {
        pendingSeek.set(frame)
    }

    override fun run() {
        var pcmStagingBuffer: ByteBuffer? = null
        try {
            if (stream.channels <= 0) return
            performInitialSeek()
            pcmStagingBuffer = allocateStagingBuffer()
            executeDecodingLoop(pcmStagingBuffer)
        } catch (e: IOException) {
            logger.error("IO error during decoding", e)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.warn("Interrupted")
        } finally {
            cleanupResources(pcmStagingBuffer)
        }
    }

    private fun performInitialSeek() {
        if (startFrame > 0) stream.seek(startFrame)
    }

    private fun allocateStagingBuffer(): ByteBuffer {
        val bufferSizeInFrames = stream.sampleRate / 4
        val pcmBufferSize = bufferSizeInFrames * stream.channels * 2
        return MemoryUtil.memAlloc(pcmBufferSize)
    }

    private fun executeDecodingLoop(pcmStagingBuffer: ByteBuffer) {
        while (!shutdown.get()) {
            handlePendingSeek()

            if (Thread.currentThread().isInterrupted) break
            if (!processSingleFrame(pcmStagingBuffer)) break
        }
    }

    private fun handlePendingSeek() {
        val seekTarget = pendingSeek.getAndSet(-1)
        if (seekTarget != -1L) {
            stream.seek(seekTarget)
            dataQueue.clear()
            dataQueue.put(SeekComplete)
        }
    }

    private fun processSingleFrame(pcmStagingBuffer: ByteBuffer): Boolean {
        pcmStagingBuffer.clear()
        val channels = stream.channels
        val samplesRead = readFromStream(pcmStagingBuffer.asShortBuffer(), channels)

        if (samplesRead <= 0) return false

        enqueueData(pcmStagingBuffer, samplesRead, channels)
        return true
    }

    private fun enqueueData(pcmStagingBuffer: ByteBuffer, samplesRead: Int, channels: Int) {
        pcmStagingBuffer.position(0).limit(samplesRead * channels * 2)

        val dataCopy = MemoryUtil.memAlloc(pcmStagingBuffer.remaining())
        dataCopy.order(ByteOrder.nativeOrder())
        dataCopy.put(pcmStagingBuffer).flip()

        dataQueue.put(dataCopy)
    }

    private fun cleanupResources(pcmStagingBuffer: ByteBuffer?) {
        stream.close()
        sendPoisonPill()
        if (pcmStagingBuffer != null) MemoryUtil.memFree(pcmStagingBuffer)
    }

    private fun sendPoisonPill() {
        try {
            dataQueue.put(PoisonPill)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun readFromStream(pcmShortBuffer: ShortBuffer, channels: Int): Int {
        pcmShortBuffer.clear()
        val framesToRead = pcmShortBuffer.capacity() / channels
        pcmShortBuffer.limit(framesToRead * channels)
        return stream.read(pcmShortBuffer)
    }

    object PoisonPill
    object SeekComplete

    companion object {
        private val logger: Logger = AcademyCraft.getLogger()
    }
}