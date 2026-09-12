package org.academy.internal.client.app.music.decoder;

import org.academy.internal.common.music.provider.AudioFormat;

import java.nio.ByteBuffer;

/**
 * 客户端解码器的格式嗅探入口，逻辑委托公共 {@link AudioFormat} 喵。
 */
public final class AudioFormatDetector {
    private AudioFormatDetector() {
    }

    public static Format detect(byte[] data) {
        return toClient(AudioFormat.detect(data));
    }

    public static Format detect(ByteBuffer data) {
        return toClient(AudioFormat.detect(data));
    }

    private static Format toClient(AudioFormat.Format format) {
        return format == null ? Format.UNKNOWN : Format.valueOf(format.name());
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
