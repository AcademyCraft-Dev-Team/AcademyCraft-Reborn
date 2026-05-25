package org.academy.internal.client.app.music.decoder.flac

import org.academy.internal.client.app.music.decoder.AudioStream
import org.jflac.FLACDecoder
import org.jflac.io.RandomFileInputStream
import org.jflac.metadata.StreamInfo
import org.jflac.util.ByteData
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ShortBuffer

class FlacAudioStream(audioData: ByteBuffer) : AudioStream {
    private val originalData: ByteBuffer = audioData
    private lateinit var decoder: FLACDecoder
    private lateinit var streamInfo: StreamInfo
    private lateinit var randomAccessStream: ByteBufferRandomFileInputStream

    private var stagingBuffer: ShortArray? = null
    private var stagingBufferReadOffset = 0
    private var stagingBufferWriteOffset = 0
    private var reusedByteData: ByteData? = null
    private var endOfStream = false

    override val channels: Int get() = streamInfo.channels
    override val sampleRate: Int get() = streamInfo.sampleRate
    override val totalSamples: Long get() = streamInfo.totalSamples

    init {
        initializeDecoder()
    }

    private fun initializeDecoder() {
        randomAccessStream = ByteBufferRandomFileInputStream(originalData.duplicate())
        decoder = FLACDecoder(randomAccessStream)
        streamInfo = decoder.readStreamInfo() ?: throw IOException("Invalid FLAC stream: Metadata not found")
        decoder.readMetadata(streamInfo)
        resetStagingState()
    }

    private fun resetStagingState() {
        stagingBuffer = null
        reusedByteData = null
        stagingBufferReadOffset = 0
        stagingBufferWriteOffset = 0
        endOfStream = false
    }

    override fun read(pcmBuffer: ShortBuffer): Int {
        val startPosition = pcmBuffer.position()
        performReadLoop(pcmBuffer)
        val totalWritten = pcmBuffer.position() - startPosition
        return if (totalWritten == 0 && endOfStream) -1 else totalWritten / channels
    }

    private fun performReadLoop(pcmBuffer: ShortBuffer) {
        while (pcmBuffer.hasRemaining()) {
            if (!ensureDataAvailable()) break
            transferToOutput(pcmBuffer)
        }
    }

    private fun ensureDataAvailable(): Boolean {
        if (!isStagingBufferEmpty()) return true
        return fetchNextFrame()
    }

    private fun isStagingBufferEmpty(): Boolean =
        stagingBuffer == null || stagingBufferReadOffset >= stagingBufferWriteOffset

    private fun fetchNextFrame(): Boolean {
        if (endOfStream) return false
        try {
            val frame = decoder.readNextFrame() ?: run {
                endOfStream = true
                return false
            }
            reusedByteData = decoder.decodeFrame(frame, reusedByteData)
            updateStagingBuffer(reusedByteData!!)
            return true
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("FLAC decoding error", e)
        }
    }

    private fun transferToOutput(pcmBuffer: ShortBuffer) {
        val buffer = stagingBuffer ?: return
        val available = stagingBufferWriteOffset - stagingBufferReadOffset
        val toCopy = minOf(pcmBuffer.remaining(), available)
        pcmBuffer.put(buffer, stagingBufferReadOffset, toCopy)
        stagingBufferReadOffset += toCopy
    }

    private fun updateStagingBuffer(byteData: ByteData) {
        val bytes = byteData.data
        val len = byteData.len
        val samplesCount = len / 2
        prepareStagingArray(samplesCount)
        convertBytesToSamples(bytes, samplesCount)
    }

    private fun prepareStagingArray(samplesCount: Int) {
        if (stagingBuffer == null || stagingBuffer!!.size < samplesCount) {
            stagingBuffer = ShortArray(samplesCount)
        }
        stagingBufferReadOffset = 0
        stagingBufferWriteOffset = samplesCount
    }

    private fun convertBytesToSamples(bytes: ByteArray, samplesCount: Int) {
        val buffer = stagingBuffer ?: return
        for (i in 0 until samplesCount) {
            val low = bytes[i * 2].toInt() and 0xFF
            val high = bytes[i * 2 + 1].toInt()
            buffer[i] = ((high shl 8) or low).toShort()
        }
    }

    override fun seek(sampleFrame: Long) {
        val targetFrame = sampleFrame.coerceIn(0, totalSamples - 1)
        try {
            randomAccessStream.resetToDuplicate()
            decoder = FLACDecoder(randomAccessStream)
            decoder.seek(targetFrame)
            resetStagingState()
        } catch (_: IOException) {
            initializeDecoder()
            var currentFrame = 0L
            while (currentFrame < targetFrame && !endOfStream) {
                val frame = decoder.readNextFrame() ?: run {
                    endOfStream = true
                    break
                }
                val blockSize = frame.header.blockSize
                if (currentFrame + blockSize <= targetFrame) {
                    currentFrame += blockSize
                } else {
                    val data = decoder.decodeFrame(frame, reusedByteData)
                    reusedByteData = data
                    updateStagingBuffer(data)
                    val framesToSkip = targetFrame - currentFrame
                    stagingBufferReadOffset = (framesToSkip * channels).toInt()
                    currentFrame += framesToSkip
                }
            }
        }
    }

    override fun close() {}

    private inner class ByteBufferRandomFileInputStream(private var buffer: ByteBuffer) :
        RandomFileInputStream(null as RandomAccessFile?) {

        override fun read(): Int {
            if (!buffer.hasRemaining()) return -1
            return buffer.get().toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (!buffer.hasRemaining()) return -1
            val length = minOf(len, buffer.remaining())
            buffer.get(b, off, length)
            return length
        }

        override fun available(): Int = buffer.remaining()

        override fun skip(n: Long): Long {
            val toSkip = minOf(n, buffer.remaining().toLong())
            buffer.position(buffer.position() + toSkip.toInt())
            return toSkip
        }

        override fun close() {}

        override fun seek(pos: Long) {
            buffer.position(pos.toInt())
        }

        override fun getLength(): Long = originalData.limit().toLong()

        fun resetToDuplicate() {
            buffer = originalData.duplicate()
        }
    }
}