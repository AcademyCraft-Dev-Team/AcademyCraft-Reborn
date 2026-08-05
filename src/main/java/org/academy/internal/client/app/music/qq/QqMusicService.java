package org.academy.internal.client.app.music.qq;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.academy.AcademyCraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class QqMusicService {
    private static final String MUSICU_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg";
    private static final String DEFAULT_SIP = "http://ws.stream.qqmusic.qq.com/";
    private static final FileCandidate[] OGG_CANDIDATES = new FileCandidate[] {
            new FileCandidate("O600", "ogg"),
            new FileCandidate("O670", "ogg")
    };

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

    public static QqResolvedTrack resolveOggTrack(String mid) throws IOException {
        if (mid == null || mid.isBlank()) {
            throw new IOException("Missing QQ music track id");
        }
        var trackInfo = getTrackInfo(mid);
        var mediaMid = trackInfo.mediaMid().isBlank() ? mid : trackInfo.mediaMid();
        var data = requestVkeyData(mid, mediaMid);
        var purl = selectBestPurl(data.getAsJsonArray("midurlinfo"));
        if (purl.isBlank()) {
            throw new IOException("QQ music track has no playable ogg source");
        }
        return new QqResolvedTrack(mid, trackInfo.title(), trackInfo.artist(), trackInfo.interval(), trackInfo.vip(), resolveBaseUrl(data) + purl);
    }

    public static byte[] downloadOggBytes(String mid) throws IOException {
        var track = resolveOggTrack(mid);
        var connection = (HttpURLConnection) URI.create(track.streamUrl()).toURL().openConnection();
        connection.setRequestProperty("User-Agent", defaultUserAgent());
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        try (var stream = connection.getInputStream()) {
            return stream.readAllBytes();
        } finally {
            connection.disconnect();
        }
    }

    private static TrackInfo getTrackInfo(String mid) throws IOException {
        var param = new JsonObject();
        param.addProperty("song_mid", mid);
        param.addProperty("song_id", 0);

        var req = new JsonObject();
        req.addProperty("module", "music.pf_song_detail_svr");
        req.addProperty("method", "get_song_detail");
        req.add("param", param);
        req.addProperty("loginUin", "0");

        var comm = new JsonObject();
        comm.addProperty("uin", "0");
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
        return new TrackInfo(title, String.join("/", singers), interval, mediaMid, albumMid, vip);
    }

    private static JsonObject requestVkeyData(String songMid, String mediaMid) throws IOException {
        var filenameList = new JsonArray();
        var songMidList = new JsonArray();
        var songTypeList = new JsonArray();
        for (var candidate : OGG_CANDIDATES) {
            filenameList.add(candidate.buildFilename(mediaMid));
            songMidList.add(songMid);
            songTypeList.add(0);
        }

        var param = new JsonObject();
        param.add("filename", filenameList);
        param.addProperty("guid", "10000");
        param.add("songmid", songMidList);
        param.add("songtype", songTypeList);
        param.addProperty("uin", "0");
        param.addProperty("loginflag", 1);
        param.addProperty("platform", "20");

        var req = new JsonObject();
        req.addProperty("module", "vkey.GetVkeyServer");
        req.addProperty("method", "CgiGetVkey");
        req.add("param", param);

        var comm = new JsonObject();
        comm.addProperty("uin", "0");
        comm.addProperty("format", "json");
        comm.addProperty("ct", 24);
        comm.addProperty("cv", 0);

        var body = new JsonObject();
        body.add("req_1", req);
        body.addProperty("loginUin", "0");
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

    private static String resolveBaseUrl(JsonObject data) {
        if (data != null && data.has("sip")) {
            var sip = data.getAsJsonArray("sip");
            if (sip != null && !sip.isEmpty()) {
                var value = sip.get(0).getAsString();
                if (value != null && !value.isBlank()) {
                    return value.endsWith("/") ? value : value + "/";
                }
            }
        }
        return DEFAULT_SIP;
    }

    private static String selectBestPurl(JsonArray midurlinfo) {
        if (midurlinfo == null) {
            return "";
        }
        for (var i = 0; i < midurlinfo.size(); i++) {
            var info = midurlinfo.get(i).getAsJsonObject();
            if (info != null && info.has("purl")) {
                var purl = info.get("purl").getAsString();
                if (purl != null && !purl.isBlank()) {
                    return purl;
                }
            }
        }
        return "";
    }

    private static String getString(JsonObject object, String key) {
        return object != null && object.has(key) ? object.get(key).getAsString() : "";
    }

    private static String defaultUserAgent() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0";
    }

    private record FileCandidate(String prefix, String extension) {
        private String buildFilename(String mediaMid) {
            return prefix + mediaMid + "." + extension;
        }
    }

    private record TrackInfo(String title, String artist, int interval, String mediaMid, String albumMid, boolean vip) {
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
}
