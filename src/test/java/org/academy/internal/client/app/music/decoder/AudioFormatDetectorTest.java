package org.academy.internal.client.app.music.decoder;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioFormatDetectorTest {
    @Test
    void distinguishesVorbisFromOtherOggCodecs() {
        assertEquals(AudioFormatDetector.Format.OGG_VORBIS,
                AudioFormatDetector.detect(oggHeader("\u0001vorbis")));
        assertEquals(AudioFormatDetector.Format.OGG_OPUS,
                AudioFormatDetector.detect(oggHeader("OpusHead")));
        assertTrue(AudioFormatDetector.Format.OGG_VORBIS.isSupported());
        assertFalse(AudioFormatDetector.Format.OGG_OPUS.isSupported());
    }

    @Test
    void detectsSupportedFlacAndMp3Payloads() {
        assertEquals(AudioFormatDetector.Format.FLAC,
                AudioFormatDetector.detect("fLaCdata".getBytes()));
        assertEquals(AudioFormatDetector.Format.MP3,
                AudioFormatDetector.detect(new byte[]{'I', 'D', '3', 4, 0, 0}));
        assertEquals(AudioFormatDetector.Format.MP3,
                AudioFormatDetector.detect(new byte[]{(byte) 0xff, (byte) 0xfb, (byte) 0x90}));
    }

    @Test
    void identifiesUnsupportedMp4InsteadOfTreatingItAsAudioFrames() {
        assertEquals(AudioFormatDetector.Format.MP4_AAC,
                AudioFormatDetector.detect(new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p'}));
    }

    @Test
    void respectsByteBufferPosition() {
        var buffer = ByteBuffer.wrap(new byte[]{9, 9, 'f', 'L', 'a', 'C'});
        buffer.position(2);
        assertEquals(AudioFormatDetector.Format.FLAC, AudioFormatDetector.detect(buffer));
        assertEquals(2, buffer.position());
    }

    private static byte[] oggHeader(String codecMarker) {
        var marker = codecMarker.getBytes();
        var bytes = new byte[32 + marker.length];
        bytes[0] = 'O';
        bytes[1] = 'g';
        bytes[2] = 'g';
        bytes[3] = 'S';
        System.arraycopy(marker, 0, bytes, 32, marker.length);
        return bytes;
    }
}
