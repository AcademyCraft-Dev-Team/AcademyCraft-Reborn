package org.academy.internal.client.app.music.netease;

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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NeteaseMusicService {
    private static final String BASE_URL = "https://music.163.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.164 NeteaseMusicDesktop/3.1.6";

    private NeteaseMusicService() {
    }

    private static Map<String, String> defaultHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Referer", "https://music.163.com");
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        var cookie = NeteaseCredentialManager.getEffectiveCookie();
        if (!cookie.isBlank()) {
            headers.put("Cookie", cookie);
        }
        return headers;
    }

    private static String buildFormBody(Map<String, Object> params) {
        var builder = new StringBuilder();
        for (var entry : params.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append("&");
            }
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            builder.append("=");
            builder.append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    private static JsonObject postJson(String url, String body, Map<String, String> headers) throws IOException {
        var connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
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
        try (var reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            var result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            return JsonParser.parseString(result.toString()).getAsJsonObject();
        } finally {
            connection.disconnect();
        }
    }

    private static JsonObject getJson(String url, Map<String, String> headers) throws IOException {
        var connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        for (var entry : headers.entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
        try (var reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            var result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            return JsonParser.parseString(result.toString()).getAsJsonObject();
        } finally {
            connection.disconnect();
        }
    }

    private static String getString(JsonObject object, String key) {
        return object != null && object.has(key) ? object.get(key).getAsString() : "";
    }

    public static List<NeteaseSearchResult> search(String query) throws IOException {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        var headers = defaultHeaders();
        Map<String, Object> params = new HashMap<>();
        params.put("s", query.trim());
        params.put("type", 1);
        params.put("offset", 0);
        params.put("limit", 20);
        params.put("total", true);

        var body = buildFormBody(params);
        var root = postJson(BASE_URL + "/api/cloudsearch/pc/", body, headers);
        if (root == null || !root.has("result")) {
            return Collections.emptyList();
        }
        var resultObj = root.getAsJsonObject("result");
        if (!resultObj.has("songs")) {
            return Collections.emptyList();
        }
        var songs = resultObj.getAsJsonArray("songs");
        List<NeteaseSearchResult> results = new ArrayList<>();
        for (var element : songs) {
            var song = element.getAsJsonObject();
            var id = getString(song, "id");
            var title = getString(song, "name");
            var duration = song.has("dt") ? song.get("dt").getAsInt() / 1000 : 0;
            var fee = song.has("fee") ? song.get("fee").getAsInt() : 0;
            List<String> artists = new ArrayList<>();
            if (song.has("ar")) {
                for (var ar : song.getAsJsonArray("ar")) {
                    artists.add(getString(ar.getAsJsonObject(), "name"));
                }
            }
            var albumName = "";
            var picUrl = "";
            if (song.has("al")) {
                var album = song.getAsJsonObject("al");
                albumName = getString(album, "name");
                picUrl = getString(album, "picUrl");
            }
            results.add(new NeteaseSearchResult(id, title, String.join("/", artists), duration, albumName, picUrl, fee));
        }
        return results;
    }

    public static String resolveStreamUrl(String songId) throws IOException {
        var url = BASE_URL + "/api/song/enhance/player/url/v1?encodeType=mp3&ids=[" + songId + "]&level=standard";
        var root = getJson(url, defaultHeaders());
        if (root == null || !root.has("data")) {
            throw new IOException("No stream URL for NetEase song " + songId);
        }
        var data = root.getAsJsonArray("data");
        if (data.isEmpty()) {
            throw new IOException("Empty stream data for NetEase song " + songId);
        }
        var first = data.get(0).getAsJsonObject();
        if (!first.has("url") || first.get("url").isJsonNull()) {
            throw new IOException("NetEase song " + songId + " is not available (no copyright or VIP-only)");
        }
        return first.get("url").getAsString();
    }

    public static byte[] downloadStreamBytes(String songId) throws IOException {
        var streamUrl = resolveStreamUrl(songId);
        var connection = (HttpURLConnection) URI.create(streamUrl).toURL().openConnection();
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Referer", "https://music.163.com");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        try (var stream = connection.getInputStream()) {
            return stream.readAllBytes();
        } finally {
            connection.disconnect();
        }
    }

    public static byte[] downloadAlbumCoverBytes(String picUrl) throws IOException {
        if (picUrl == null || picUrl.isBlank()) {
            throw new IOException("Missing album cover URL");
        }
        var connection = (HttpURLConnection) URI.create(picUrl).toURL().openConnection();
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Referer", "https://music.163.com");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        try (var stream = connection.getInputStream()) {
            return stream.readAllBytes();
        } finally {
            connection.disconnect();
        }
    }
}
