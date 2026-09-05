package org.academy.internal.client.app.music.qq;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void diagnosesVipTrackWithoutLoginAsLoginRequired() {
        assertEquals(
                "付费歌曲需登录 QQ 音乐账号（VIP）后才能播放",
                QqMusicService.diagnoseNoSource(true, null, 0)
        );
    }

    @Test
    void diagnosesVipTrackWithExpiredCredentialAsReLogin() {
        var expired = new QqCredential(
                "123", "key", 3600L, System.currentTimeMillis() / 1000 - 7200, "", ""
        );
        assertEquals(
                "QQ 音乐登录已过期，请重新登录后再播放付费歌曲",
                QqMusicService.diagnoseNoSource(true, expired, 0)
        );
    }

    @Test
    void diagnosesVipTrackWithValidCredentialAsMissingVipRight() {
        var valid = new QqCredential(
                "123", "key", 3600L, System.currentTimeMillis() / 1000, "", ""
        );
        assertEquals(
                "付费歌曲暂时无法播放，请确认账号具备 VIP 权限",
                QqMusicService.diagnoseNoSource(true, valid, 0)
        );
    }

    @Test
    void diagnosesFreeTrackWithApiErrorCode() {
        assertEquals(
                "QQ 音乐未返回可播放的音频源（code=40000）",
                QqMusicService.diagnoseNoSource(false, null, 40000)
        );
    }

    @Test
    void diagnosesFreeTrackWithoutApiErrorCode() {
        assertEquals(
                "QQ 音乐未返回可播放的音频源",
                QqMusicService.diagnoseNoSource(false, null, 0)
        );
    }

    @Test
    void diagnosesPermissionErrorOnFreeTrackAsVipOnly() {
        assertEquals(
                "该歌曲为付费/VIP 曲目，当前账号无播放权限",
                QqMusicService.diagnoseNoSource(false, null, 104009)
        );
    }

    @Test
    void diagnosesPermissionErrorOnVipTrackWithoutLoginAsLoginRequired() {
        assertEquals(
                "付费歌曲需登录 QQ 音乐账号（VIP）后才能播放",
                QqMusicService.diagnoseNoSource(true, null, 104009)
        );
    }

    @Test
    void diagnosesPermissionErrorOnVipTrackWithExpiredCredentialAsReLogin() {
        var expired = new QqCredential(
                "123", "key", 3600L, System.currentTimeMillis() / 1000 - 7200, "", ""
        );
        assertEquals(
                "QQ 音乐登录已过期，请重新登录后再播放付费歌曲",
                QqMusicService.diagnoseNoSource(true, expired, 104009)
        );
    }

    @Test
    void diagnosesPermissionErrorOnVipTrackWithValidCredentialAsMissingVipRight() {
        var valid = new QqCredential(
                "123", "key", 3600L, System.currentTimeMillis() / 1000, "", ""
        );
        assertEquals(
                "付费歌曲暂时无法播放，请确认账号具备 VIP 权限",
                QqMusicService.diagnoseNoSource(true, valid, 104009)
        );
    }

    private static byte[] throwFailure() throws IOException {
        throw new IOException("unavailable");
    }
}
