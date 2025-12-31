package org.academy.internal.client.app.music.backend.decoder;

import org.academy.AcademyCraft;
import org.academy.internal.client.app.music.backend.decoder.flac.FlacAudioStream;
import org.academy.internal.client.app.music.backend.decoder.mp3.Mp3AudioStream;
import org.academy.internal.client.app.music.backend.decoder.ogg.VorbisAudioStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;

public final class DecoderFactory {
    private static final Logger LOGGER = AcademyCraft.getLogger();

    private DecoderFactory() {
    }

    @Nullable
    public static AudioStream create(ByteBuffer audioData) {
        try {
            if (isOgg(audioData)) return new VorbisAudioStream(audioData);
            else if (isFlac(audioData)) return new FlacAudioStream(audioData);
            else return new Mp3AudioStream(audioData);
        } catch (IOException e) {
            LOGGER.error("Failed to initialize audio decoder.", e);
            return null;
        }
    }

    private static boolean isOgg(ByteBuffer data) {
        if (data.remaining() < 4) return false;
        var pos = data.position();
        return data.get(pos) == 'O' &&
                data.get(pos + 1) == 'g' &&
                data.get(pos + 2) == 'g' &&
                data.get(pos + 3) == 'S';
    }

    private static boolean isFlac(ByteBuffer data) {
        if (data.remaining() < 4) return false;
        var pos = data.position();
        return data.get(pos) == 'f' &&
                data.get(pos + 1) == 'L' &&
                data.get(pos + 2) == 'a' &&
                data.get(pos + 3) == 'C';
    }
}