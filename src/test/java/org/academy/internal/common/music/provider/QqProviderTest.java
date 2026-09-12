package org.academy.internal.common.music.provider;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QqProviderTest {
    @Test
    void usesAdvertisedSupportedFormatsInQualityOrder() {
        assertEquals(
                List.of("M800media.mp3", "M500media.mp3", "F000media.flac"),
                QqProvider.buildSupportedFilenames("media", 5L, 10L, 20L)
        );
    }

    @Test
    void legacyTracksUseAvailableMp3InsteadOfNonexistentOgg() {
        assertEquals(
                List.of("M500media.mp3"),
                QqProvider.buildSupportedFilenames("media", 5L, 0L, 0L)
        );
    }

    @Test
    void missingAvailabilityMetadataStillAttemptsCompatibleMp3() {
        assertEquals(
                List.of("M500media.mp3"),
                QqProvider.buildSupportedFilenames("media", 0L, 0L, 0L)
        );
    }

    @Test
    void failedAndUnsupportedSourcesFallBackToDecodableAudio() throws IOException {
        var attempts = new ArrayList<String>();
        var mp3 = new byte[]{'I', 'D', '3', 4, 0, 0};

        var result = MusicProviders.downloadFirstSupported(
                List.of("missing", "m4a", "mp3"),
                url -> {
                    attempts.add(url);
                    return switch (url) {
                        case "missing" -> throw new IOException("HTTP 404");
                        case "m4a" -> throw new IOException("unsupported audio format MP4_AAC");
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
                MusicProviders.downloadFirstSupported(
                        List.of("first", "second"),
                        _ -> throwFailure()
                ));

        assertEquals(2, exception.getSuppressed().length);
    }

    @Test
    void diagnosesVipTrackWithoutLoginAsLoginRequired() {
        assertEquals(
                "付费歌曲需登录 QQ 音乐账号（VIP）后才能播放",
                QqProvider.diagnoseNoSource(true, ProviderCredential.ANONYMOUS, 0)
        );
    }

    @Test
    void diagnosesVipTrackWithExpiredCredentialAsReLogin() {
        var expired = ProviderCredential.ofQq("123", "key", System.currentTimeMillis() / 1000 - 3600);
        assertEquals(
                "QQ 音乐登录已过期，请重新登录后再播放付费歌曲",
                QqProvider.diagnoseNoSource(true, expired, 0)
        );
    }

    @Test
    void diagnosesVipTrackWithValidCredentialAsMissingVipRight() {
        var valid = ProviderCredential.ofQq("123", "key", System.currentTimeMillis() / 1000 + 3600);
        assertEquals(
                "付费歌曲暂时无法播放，请确认账号具备 VIP 权限",
                QqProvider.diagnoseNoSource(true, valid, 0)
        );
    }

    @Test
    void diagnosesFreeTrackWithApiErrorCode() {
        assertEquals(
                "QQ 音乐未返回可播放的音频源（code=40000）",
                QqProvider.diagnoseNoSource(false, ProviderCredential.ANONYMOUS, 40000)
        );
    }

    @Test
    void diagnosesFreeTrackWithoutApiErrorCode() {
        assertEquals(
                "QQ 音乐未返回可播放的音频源",
                QqProvider.diagnoseNoSource(false, ProviderCredential.ANONYMOUS, 0)
        );
    }

    @Test
    void diagnosesPermissionErrorOnFreeTrackAsVipOnly() {
        assertEquals(
                "该歌曲为付费/VIP 曲目，当前账号无播放权限",
                QqProvider.diagnoseNoSource(false, ProviderCredential.ANONYMOUS, 104009)
        );
    }

    @Test
    void diagnosesPermissionErrorOnVipTrackWithoutLoginAsLoginRequired() {
        assertEquals(
                "付费歌曲需登录 QQ 音乐账号（VIP）后才能播放",
                QqProvider.diagnoseNoSource(true, ProviderCredential.ANONYMOUS, 104009)
        );
    }

    @Test
    void diagnosesPermissionErrorOnVipTrackWithExpiredCredentialAsReLogin() {
        var expired = ProviderCredential.ofQq("123", "key", System.currentTimeMillis() / 1000 - 3600);
        assertEquals(
                "QQ 音乐登录已过期，请重新登录后再播放付费歌曲",
                QqProvider.diagnoseNoSource(true, expired, 104009)
        );
    }

    @Test
    void diagnosesPermissionErrorOnVipTrackWithValidCredentialAsMissingVipRight() {
        var valid = ProviderCredential.ofQq("123", "key", System.currentTimeMillis() / 1000 + 3600);
        assertEquals(
                "付费歌曲暂时无法播放，请确认账号具备 VIP 权限",
                QqProvider.diagnoseNoSource(true, valid, 104009)
        );
    }

    private static byte[] throwFailure() throws IOException {
        throw new IOException("unavailable");
    }
}
