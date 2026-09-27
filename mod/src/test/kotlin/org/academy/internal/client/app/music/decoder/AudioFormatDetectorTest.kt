package org.academy.internal.client.app.music.decoder

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class AudioFormatDetectorTest {
    @Test
    fun distinguishesVorbisFromOtherOggCodecs() {
        assertEquals(
            AudioFormatDetector.Format.OGG_VORBIS,
            AudioFormatDetector.detect(oggHeader("\u0001vorbis")),
        )
        assertEquals(
            AudioFormatDetector.Format.OGG_OPUS,
            AudioFormatDetector.detect(oggHeader("OpusHead")),
        )
        assertTrue(AudioFormatDetector.Format.OGG_VORBIS.supported)
        assertFalse(AudioFormatDetector.Format.OGG_OPUS.supported)
    }

    @Test
    fun detectsSupportedFlacAndMp3Payloads() {
        assertEquals(
            AudioFormatDetector.Format.FLAC,
            AudioFormatDetector.detect("fLaCdata".toByteArray()),
        )
        assertEquals(
            AudioFormatDetector.Format.MP3,
            AudioFormatDetector.detect(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0)),
        )
        assertEquals(
            AudioFormatDetector.Format.MP3,
            AudioFormatDetector.detect(byteArrayOf(0xff.toByte(), 0xfb.toByte(), 0x90.toByte())),
        )
    }

    @Test
    fun identifiesUnsupportedMp4InsteadOfTreatingItAsAudioFrames() {
        assertEquals(
            AudioFormatDetector.Format.MP4_AAC,
            AudioFormatDetector.detect(
                byteArrayOf(
                    0, 0, 0, 24,
                    'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
                )
            ),
        )
    }

    @Test
    fun respectsByteBufferPosition() {
        val buffer = ByteBuffer.wrap(
            byteArrayOf(9, 9, 'f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte())
        )
        buffer.position(2)
        assertEquals(AudioFormatDetector.Format.FLAC, AudioFormatDetector.detect(buffer))
        assertEquals(2, buffer.position())
    }

    private fun oggHeader(codecMarker: String): ByteArray {
        val marker = codecMarker.toByteArray()
        val bytes = ByteArray(32 + marker.size)
        bytes[0] = 'O'.code.toByte()
        bytes[1] = 'g'.code.toByte()
        bytes[2] = 'g'.code.toByte()
        bytes[3] = 'S'.code.toByte()
        System.arraycopy(marker, 0, bytes, 32, marker.size)
        return bytes
    }
}
