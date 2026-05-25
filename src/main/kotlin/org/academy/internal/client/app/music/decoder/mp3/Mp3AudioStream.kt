package org.academy.internal.client.app.music.decoder.mp3

import javazoom.jl.decoder.*
import org.academy.AcademyCraft
import org.academy.internal.client.app.music.decoder.AudioStream
import org.academy.internal.client.app.music.decoder.ByteBufferInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ShortBuffer

class Mp3AudioStream(audioData: ByteBuffer) : AudioStream {
    private val originalData: ByteBuffer = audioData
    override val channels: Int
    override val sampleRate: Int
    override val totalSamples: Long

    private var bitstream: Bitstream? = null
    private var decoder: Decoder? = null
    private var currentBuffer: SampleBuffer? = null
    private var bufferOffset = 0

    init {
        val header = readFirstFrame(audioData)
        channels = if (header.mode() == Header.SINGLE_CHANNEL) 1 else 2
        sampleRate = header.frequency()
        val frameCount = header.maxNumberOfFrames(audioData.remaining())
        totalSamples = frameCount * getFrameSampleCount(header).toLong()
        resetState()
    }

    private fun readFirstFrame(audioData: ByteBuffer): Header {
        ByteBufferInputStream(audioData.duplicate()).use { tempStream ->
            val scanBs = Bitstream(tempStream)
            val header = scanBs.readFrame()
            scanBs.close()
            if (header == null) throw IOException("Empty MP3 stream")
            return header
        }
    }

    override fun read(pcmBuffer: ShortBuffer): Int {
        val stream = bitstream
        val decoder = decoder
        if (stream == null || decoder == null) return -1
        val startPos = pcmBuffer.position()
        val buffer = currentBuffer
        while (pcmBuffer.hasRemaining() && buffer != null &&
            (bufferOffset < buffer.bufferLength || decodeNextFrame(stream, decoder))
        ) {
            transferTo(pcmBuffer, buffer)
        }
        val totalWritten = pcmBuffer.position() - startPos
        return if (totalWritten > 0) totalWritten / channels else -1
    }

    private fun decodeNextFrame(bitstream: Bitstream, decoder: Decoder): Boolean {
        try {
            val header = bitstream.readFrame() ?: return false
            currentBuffer = decoder.decodeFrame(header, bitstream) as SampleBuffer
            bufferOffset = 0
            bitstream.closeFrame()
            return true
        } catch (e: Exception) {
            logger.error("MP3 decode error", e)
            return false
        }
    }

    private fun transferTo(pcmBuffer: ShortBuffer, currentBuffer: SampleBuffer) {
        val available = currentBuffer.bufferLength - bufferOffset
        val toCopy = minOf(pcmBuffer.remaining(), available)
        pcmBuffer.put(currentBuffer.buffer, bufferOffset, toCopy)
        bufferOffset += toCopy
    }

    override fun seek(sampleFrame: Long) {
        resetState()
        val bs = bitstream
        val dec = decoder
        if (bs == null || dec == null) return
        try {
            advanceToSample(bs, dec, sampleFrame)
        } catch (e: BitstreamException) {
            throw IOException("Failed to seek in MP3 stream", e)
        } catch (e: DecoderException) {
            throw IOException("Failed to seek in MP3 stream", e)
        }
    }

    private fun advanceToSample(bs: Bitstream, dec: Decoder, targetSample: Long) {
        var currentSample = 0L
        while (currentSample < targetSample) {
            val header = bs.readFrame() ?: break
            val frameLen = getFrameSampleCount(header).toLong()
            if (currentSample + frameLen > targetSample) {
                currentBuffer = dec.decodeFrame(header, bs) as SampleBuffer
                bufferOffset = (targetSample - currentSample).toInt()
                bs.closeFrame()
                break
            }
            currentSample += frameLen
            bs.closeFrame()
        }
    }

    private fun resetState() {
        closeBitstream()
        decoder = Decoder()
        bitstream = Bitstream(ByteBufferInputStream(originalData.duplicate()))
        currentBuffer = null
        bufferOffset = 0
    }

    private fun closeBitstream() {
        bitstream?.let {
            try {
                it.close()
            } catch (e: BitstreamException) {
                logger.error("Error closing bitstream", e)
            }
            bitstream = null
        }
    }

    private fun getFrameSampleCount(h: Header): Int =
        when (h.layer()) {
            1 -> 384
            2 -> 1152
            else -> if (h.version() == Header.MPEG1) 1152 else 576
        }

    override fun close() {
        closeBitstream()
        decoder = null
    }

    companion object {
        private val logger = AcademyCraft.getLogger()
    }
}