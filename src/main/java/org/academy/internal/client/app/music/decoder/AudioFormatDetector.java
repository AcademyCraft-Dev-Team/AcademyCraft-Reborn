package org.academy.internal.client.app.music.decoder;

import java.nio.ByteBuffer;

public final class AudioFormatDetector {
    private static final int OGG_IDENTIFICATION_SCAN_BYTES = 4096;

    private AudioFormatDetector() {
    }

    public static Format detect(byte[] data) {
        return data == null ? Format.UNKNOWN : detect(ByteBuffer.wrap(data));
    }

    public static Format detect(ByteBuffer data) {
        if (data == null) return Format.UNKNOWN;
        var start = data.position();
        var remaining = data.remaining();
        if (remaining >= 4 && matches(data, start, "OggS")) {
            var scanLength = Math.min(remaining, OGG_IDENTIFICATION_SCAN_BYTES);
            if (contains(data, start, scanLength, "OpusHead")) return Format.OGG_OPUS;
            if (contains(data, start, scanLength, "vorbis")) return Format.OGG_VORBIS;
            return Format.OGG_UNKNOWN;
        }
        if (remaining >= 4 && matches(data, start, "fLaC")) return Format.FLAC;
        if (remaining >= 3 && matches(data, start, "ID3")) return Format.MP3;
        if (remaining >= 3 && isMpegAudioFrame(data, start)) return Format.MP3;
        if (remaining >= 8 && matches(data, start + 4, "ftyp")) return Format.MP4_AAC;
        return Format.UNKNOWN;
    }

    private static boolean isMpegAudioFrame(ByteBuffer data, int start) {
        var first = Byte.toUnsignedInt(data.get(start));
        var second = Byte.toUnsignedInt(data.get(start + 1));
        var third = Byte.toUnsignedInt(data.get(start + 2));
        if (first != 0xff || (second & 0xe0) != 0xe0) return false;
        var version = (second >>> 3) & 0x03;
        var layer = (second >>> 1) & 0x03;
        var bitrate = (third >>> 4) & 0x0f;
        var sampleRate = (third >>> 2) & 0x03;
        return version != 0x01
                && layer != 0x00
                && bitrate != 0x00
                && bitrate != 0x0f
                && sampleRate != 0x03;
    }

    private static boolean contains(
            ByteBuffer data,
            int start,
            int length,
            String marker
    ) {
        var lastStart = start + length - marker.length();
        for (var index = start; index <= lastStart; index++) {
            if (matches(data, index, marker)) return true;
        }
        return false;
    }

    private static boolean matches(ByteBuffer data, int start, String marker) {
        if (start < data.position() || start + marker.length() > data.limit()) return false;
        for (var index = 0; index < marker.length(); index++) {
            if (data.get(start + index) != (byte) marker.charAt(index)) return false;
        }
        return true;
    }

    public enum Format {
        OGG_VORBIS(true),
        FLAC(true),
        MP3(true),
        OGG_OPUS(false),
        OGG_UNKNOWN(false),
        MP4_AAC(false),
        UNKNOWN(false);

        private final boolean supported;

        Format(boolean supported) {
            this.supported = supported;
        }

        public boolean isSupported() {
            return supported;
        }
    }
}
