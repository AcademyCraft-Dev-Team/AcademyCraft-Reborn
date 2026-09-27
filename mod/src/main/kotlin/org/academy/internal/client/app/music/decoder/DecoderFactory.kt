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
            return when (val format = AudioFormatDetector.detect(audioData)) {
                AudioFormatDetector.Format.OGG_VORBIS -> VorbisAudioStream(audioData)
                AudioFormatDetector.Format.FLAC -> FlacAudioStream(audioData)
                AudioFormatDetector.Format.MP3 -> Mp3AudioStream(audioData)
                else -> {
                    logger.error("Unsupported audio format: {}.", format)
                    null
                }
            }
        } catch (e: IOException) {
            logger.error("Failed to initialize audio decoder.", e)
            return null
        }
    }
}
