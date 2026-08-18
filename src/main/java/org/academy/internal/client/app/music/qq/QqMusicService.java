package org.academy.internal.client.app.music.qq;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.academy.internal.client.app.music.decoder.AudioFormatDetector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class QqMusicService {
    private static final String MUSICU_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg";
    private static final String DEFAULT_SIP = "http://ws.stream.qqmusic.qq.com/";

    private QqMusicService() {
    }

    public static List<QqSearchResult> search(String query) throws IOException {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        var comm = new JsonObject();
        comm.addProperty("ct", "19");
        comm.addProperty("cv", "1859");
        comm.addProperty("uin", "0");

        var param = new JsonObject();
        param.addProperty("grp", 1);
        param.addProperty("num_per_page", 20);
        param.addProperty("page_num", 1);
        param.addProperty("query", query);
        param.addProperty("search_type", 0);

        var req = new JsonObject();
        req.addProperty("method", "DoSearchForQQMusicDesktop");
        req.addProperty("module", "music.search.SearchCgiService");
        req.add("param", param);

        var request = new JsonObject();
        request.add("comm", comm);
        request.add("req", req);

        var root = postJson(request.toString(), defaultHeaders(true));
        var list = root.getAsJsonObject("req")
                .getAsJsonObject("data")
                .getAsJsonObject("body")
                .getAsJsonObject("song")
                .getAsJsonArray("list");
        List<QqSearchResult> results = new ArrayList<>();
        for (var element : list) {
            var song = element.getAsJsonObject();
            var id = getString(song, "mid");
            var title = getString(song, "name");
            var vip = song.has("pay") && song.getAsJsonObject("pay").has("pay_play")
                    && song.getAsJsonObject("pay").get("pay_play").getAsInt() == 1;
            List<String> singers = new ArrayList<>();
            for (var singerElement : song.getAsJsonArray("singer")) {
                singers.add(getString(singerElement.getAsJsonObject(), "name"));
            }
            results.add(new QqSearchResult(id, title, String.join("/", singers), vip));
        }
        return results;
    }

    public static List<QqResolvedTrack> resolveTrackCandidates(String mid) throws IOException {
        if (mid == null || mid.isBlank()) {
            throw new IOException("Missing QQ music track id");
        }
        var trackInfo = getTrackInfo(mid);
        var mediaMid = trackInfo.mediaMid().isBlank() ? mid : trackInfo.mediaMid();
        var filenames = buildSupportedFilenames(
                mediaMid,
                trackInfo.size128Mp3(),
                trackInfo.size320Mp3(),
                trackInfo.sizeFlac()
        );
        var data = requestVkeyData(mid, filenames);
        var streamUrls = resolveStreamUrls(data);
        if (streamUrls.isEmpty()) {
            throw new IOException("QQ music track has no playable supported source");
        }
        return streamUrls.stream()
                .map(url -> new QqResolvedTrack(
                        mid,
                        trackInfo.title(),
                        trackInfo.artist(),
                        trackInfo.interval(),
                        trackInfo.vip(),
                        url
                ))
                .toList();
    }

    public static byte[] downloadAudioBytes(String mid) throws IOException {
        var streamUrls = resolveTrackCandidates(mid).stream()
                .map(QqResolvedTrack::streamUrl)
                .toList();
        return downloadFirstSupported(streamUrls, QqMusicService::downloadUrlBytes);
    }

    static byte[] downloadFirstSupported(
            List<String> streamUrls,
            AudioDownloader downloader
    ) throws IOException {
        var failure = new IOException("QQ music track has no downloadable supported source");
        for (var streamUrl : streamUrls) {
            try {
                var bytes = downloader.download(streamUrl);
                var format = AudioFormatDetector.detect(bytes);
                if (!format.isSupported()) {
                    throw new IOException("QQ music source returned unsupported audio format " + format);
                }
                return bytes;
            } catch (IOException exception) {
                failure.addSuppressed(exception);
            }
        }
        throw failure;
    }

    private static byte[] downloadUrlBytes(String streamUrl) throws IOException {
        var connection = (HttpURLConnection) URI.create(streamUrl).toURL().openConnection();
        connection.setRequestProperty("User-Agent", defaultUserAgent());
        connection.setRequestProperty("Referer", "https://y.qq.com/");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        var responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            connection.disconnect();
            throw new IOException("QQ music CDN returned HTTP " + responseCode);
        }
        try (var stream = connection.getInputStream()) {
            return stream.readAllBytes();
        } finally {
            connection.disconnect();
        }
    }

    private static TrackInfo getTrackInfo(String mid) throws IOException {
        var uin = effectiveUin();
        var param = new JsonObject();
        param.addProperty("song_mid", mid);
        param.addProperty("song_id", 0);

        var req = new JsonObject();
        req.addProperty("module", "music.pf_song_detail_svr");
        req.addProperty("method", "get_song_detail");
        req.add("param", param);
        req.addProperty("loginUin", uin);

        var comm = new JsonObject();
        comm.addProperty("uin", uin);
        comm.addProperty("format", "json");
        comm.addProperty("ct", 24);
        comm.addProperty("cv", 0);

        var body = new JsonObject();
        body.add("req_1", req);
        body.add("comm", comm);

        var root = postJson(body.toString(), defaultHeaders(true));
        var trackInfo = root.getAsJsonObject("req_1")
                .getAsJsonObject("data")
                .getAsJsonObject("track_info");
        var title = getString(trackInfo, "name");
        var interval = trackInfo.has("interval") ? trackInfo.get("interval").getAsInt() : 0;
        var vip = trackInfo.has("pay") && trackInfo.getAsJsonObject("pay").has("pay_play")
                && trackInfo.getAsJsonObject("pay").get("pay_play").getAsInt() == 1;
        var mediaMid = "";
        if (trackInfo.has("file") && trackInfo.getAsJsonObject("file").has("media_mid")) {
            mediaMid = getString(trackInfo.getAsJsonObject("file"), "media_mid");
        }
        var file = trackInfo.has("file") ? trackInfo.getAsJsonObject("file") : new JsonObject();
        var albumMid = "";
        if (trackInfo.has("album") && trackInfo.getAsJsonObject("album").has("mid")) {
            albumMid = getString(trackInfo.getAsJsonObject("album"), "mid");
        }
        List<String> singers = new ArrayList<>();
        if (trackInfo.has("singer")) {
            for (var singerElement : trackInfo.getAsJsonArray("singer")) {
                singers.add(getString(singerElement.getAsJsonObject(), "name"));
            }
        }
        return new TrackInfo(
                title,
                String.join("/", singers),
                interval,
                mediaMid,
                albumMid,
                vip,
                getLong(file, "size_128mp3"),
                getLong(file, "size_320mp3"),
                getLong(file, "size_flac")
        );
    }

    private static JsonObject requestVkeyData(
            String songMid,
            List<String> filenames
    ) throws IOException {
        var filenameList = new JsonArray();
        var songMidList = new JsonArray();
        var songTypeList = new JsonArray();
        for (var filename : filenames) {
            filenameList.add(filename);
            songMidList.add(songMid);
            songTypeList.add(0);
        }

        var uin = effectiveUin();

        var param = new JsonObject();
        param.add("filename", filenameList);
        param.addProperty("guid", "10000");
        param.add("songmid", songMidList);
        param.add("songtype", songTypeList);
        param.addProperty("uin", uin);
        param.addProperty("loginflag", 1);
        param.addProperty("platform", "20");

        var req = new JsonObject();
        req.addProperty("module", "vkey.GetVkeyServer");
        req.addProperty("method", "CgiGetVkey");
        req.add("param", param);

        var comm = new JsonObject();
        comm.addProperty("uin", uin);
        comm.addProperty("format", "json");
        comm.addProperty("ct", 24);
        comm.addProperty("cv", 0);

        var body = new JsonObject();
        body.add("req_1", req);
        body.addProperty("loginUin", uin);
        body.add("comm", comm);

        var root = postJson(body.toString(), defaultHeaders(true));
        return root.getAsJsonObject("req_1").getAsJsonObject("data");
    }

    private static JsonObject postJson(String body, Map<String, String> headers) throws IOException {
        var connection = (HttpURLConnection) new URL(MUSICU_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        for (var entry : headers.entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
        try (var outputStream = connection.getOutputStream()) {
            outputStream.write(body.getBytes(StandardCharsets.UTF_8));
        }
        try (var bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            var result = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                result.append(line);
            }
            return JsonParser.parseString(result.toString()).getAsJsonObject();
        } finally {
            connection.disconnect();
        }
    }

    private static Map<String, String> defaultHeaders(boolean allowCookie) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", defaultUserAgent());
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.3,en;q=0.2");
        headers.put("Content-Type", "application/json;charset=utf-8");
        headers.put("Referer", "https://y.qq.com/");
        if (allowCookie) {
            var cookie = QqCredentialManager.getEffectiveCookie();
            if (!cookie.isBlank()) {
                headers.put("Cookie", cookie);
            }
        }
        return headers;
    }

    private static List<String> resolveBaseUrls(JsonObject data) {
        var result = new LinkedHashSet<String>();
        if (data != null && data.has("sip")) {
            var sip = data.getAsJsonArray("sip");
            if (sip != null) {
                for (var element : sip) {
                    var value = element.getAsString();
                    if (value == null || value.isBlank()) continue;
                    var normalized = value.endsWith("/") ? value : value + "/";
                    result.add(normalized);
                    if (normalized.startsWith("http://")) {
                        result.add("https://" + normalized.substring("http://".length()));
                    }
                }
            }
        }
        if (result.isEmpty()) result.add(DEFAULT_SIP);
        return List.copyOf(result);
    }

    private static List<String> resolveStreamUrls(JsonObject data) {
        if (data == null || !data.has("midurlinfo")) return Collections.emptyList();
        var baseUrls = resolveBaseUrls(data);
        var result = new LinkedHashSet<String>();
        for (var element : data.getAsJsonArray("midurlinfo")) {
            var info = element.getAsJsonObject();
            if (info == null || !info.has("purl")) continue;
            var purl = info.get("purl").getAsString();
            if (purl == null || purl.isBlank()) continue;
            for (var baseUrl : baseUrls) result.add(baseUrl + purl);
        }
        return List.copyOf(result);
    }

    static List<String> buildSupportedFilenames(
            String mediaMid,
            long size128Mp3,
            long size320Mp3,
            long sizeFlac
    ) {
        if (mediaMid == null || mediaMid.isBlank()) return Collections.emptyList();
        var filenames = new ArrayList<String>();
        if (size320Mp3 > 0) filenames.add("M800" + mediaMid + ".mp3");
        if (size128Mp3 > 0) filenames.add("M500" + mediaMid + ".mp3");
        if (sizeFlac > 0) filenames.add("F000" + mediaMid + ".flac");
        if (filenames.isEmpty()) filenames.add("M500" + mediaMid + ".mp3");
        return List.copyOf(filenames);
    }

    private static String effectiveUin() {
        var credential = QqCredentialManager.getCredential();
        return credential != null && credential.isValid()
                ? credential.getMusicId()
                : "0";
    }

    private static long getLong(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return 0L;
        try {
            return object.get(key).getAsLong();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static String getString(JsonObject object, String key) {
        return object != null && object.has(key) ? object.get(key).getAsString() : "";
    }

    private static String defaultUserAgent() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0";
    }

    public static byte[] downloadAlbumCoverBytes(String albumMid) throws IOException {
        if (albumMid == null || albumMid.isBlank()) {
            throw new IOException("Missing album mid for cover download");
        }
        var coverUrl = "https://y.qq.com/music/photo_new/T002R300x300M000" + albumMid + ".jpg";
        var connection = (HttpURLConnection) URI.create(coverUrl).toURL().openConnection();
        connection.setRequestProperty("User-Agent", defaultUserAgent());
        connection.setRequestProperty("Referer", "https://y.qq.com/");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        try (var stream = connection.getInputStream()) {
            return stream.readAllBytes();
        } finally {
            connection.disconnect();
        }
    }

    public static String resolveAlbumMid(String mid) throws IOException {
        var info = getTrackInfo(mid);
        return info.albumMid();
    }

    private record TrackInfo(
            String title,
            String artist,
            int interval,
            String mediaMid,
            String albumMid,
            boolean vip,
            long size128Mp3,
            long size320Mp3,
            long sizeFlac
    ) {
    }

    @FunctionalInterface
    interface AudioDownloader {
        byte[] download(String streamUrl) throws IOException;
    }
}
