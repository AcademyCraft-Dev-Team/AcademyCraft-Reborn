package org.academy.internal.client.app.music.decoder

import org.academy.AcademyCraft
import org.academy.internal.client.app.music.decoder.flac.FlacAudioStream
import org.academy.internal.client.app.music.decoder.mp3.Mp3AudioStream
import org.academy.internal.client.app.music.decoder.ogg.VorbisAudioStream
import java.io.IOException
import java.nio.ByteBuffer

object DecoderFactory {
    private val logger = AcademyCraft.getLogger()

    fun create(audioData: ByteBuffer): AudioStream? {
        try {
            if (isOgg(audioData)) return VorbisAudioStream(audioData)
            else if (isFlac(audioData)) return FlacAudioStream(audioData)
            else if (isMp3(audioData)) return Mp3AudioStream(audioData)

            logger.error("Unsupported audio format.")
            return null
        } catch (e: IOException) {
            logger.error("Failed to initialize audio decoder.", e)
            return null
        }
    }

    private fun isOgg(data: ByteBuffer): Boolean {
        if (data.remaining() < 4) return false
        val pos = data.position()
        return data.get(pos) == 'O'.code.toByte() && data.get(pos + 1) == 'g'.code.toByte() && data.get(pos + 2) == 'g'.code.toByte() && data.get(
            pos + 3
        ) == 'S'.code.toByte()
    }

    private fun isFlac(data: ByteBuffer): Boolean {
        if (data.remaining() < 4) return false
        val pos = data.position()
        return data.get(pos) == 'f'.code.toByte() && data.get(pos + 1) == 'L'.code.toByte() && data.get(pos + 2) == 'a'.code.toByte() && data.get(
            pos + 3
        ) == 'C'.code.toByte()
    }

    private fun isMp3(data: ByteBuffer): Boolean {
        if (data.remaining() < 3) return false
        val pos = data.position()

        if (data.get(pos) == 'I'.code.toByte() && data.get(pos + 1) == 'D'.code.toByte() && data.get(pos + 2) == '3'.code.toByte()) return true

        return (data.get(pos).toInt() and 0xFF) == 0xFF && (data.get(pos + 1).toInt() and 0xE0) == 0xE0
    }
}