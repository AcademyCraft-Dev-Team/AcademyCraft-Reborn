package org.academy.internal.client.app.music.qq;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QqMusicServiceTest {
    @Test
    void usesAdvertisedSupportedFormatsInQualityOrder() {
        assertEquals(
                List.of("M800media.mp3", "M500media.mp3", "F000media.flac"),
                QqMusicService.buildSupportedFilenames("media", 5L, 10L, 20L)
        );
    }

    @Test
    void legacyTracksUseAvailableMp3InsteadOfNonexistentOgg() {
        assertEquals(
                List.of("M500media.mp3"),
                QqMusicService.buildSupportedFilenames("media", 5L, 0L, 0L)
        );
    }

    @Test
    void missingAvailabilityMetadataStillAttemptsCompatibleMp3() {
        assertEquals(
                List.of("M500media.mp3"),
                QqMusicService.buildSupportedFilenames("media", 0L, 0L, 0L)
        );
    }

    @Test
    void failedAndUnsupportedSourcesFallBackToDecodableAudio() throws IOException {
        var attempts = new ArrayList<String>();
        var mp3 = new byte[]{'I', 'D', '3', 4, 0, 0};

        var result = QqMusicService.downloadFirstSupported(
                List.of("missing", "m4a", "mp3"),
                url -> {
                    attempts.add(url);
                    return switch (url) {
                        case "missing" -> throw new IOException("HTTP 404");
                        case "m4a" -> new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p'};
                        default -> mp3;
                    };
                }
        );

        assertArrayEquals(mp3, result);
        assertEquals(List.of("missing", "m4a", "mp3"), attempts);
    }

    @Test
    void reportsFailureAfterAllSourcesAreExhausted() {
        var exception = assertThrows(IOException.class, () ->
                QqMusicService.downloadFirstSupported(
                        List.of("first", "second"),
                        _ -> throwFailure()
                ));

        assertEquals(2, exception.getSuppressed().length);
    }

    private static byte[] throwFailure() throws IOException {
        throw new IOException("unavailable");
    }
}
