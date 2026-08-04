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
        String cookie = NeteaseCredentialManager.getEffectiveCookie();
        if (!cookie.isBlank()) {
            headers.put("Cookie", cookie);
        }
        return headers;
    }

    private static String buildFormBody(Map<String, Object> params) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
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
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(body.getBytes(StandardCharsets.UTF_8));
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder result = new StringBuilder();
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
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder result = new StringBuilder();
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
        Map<String, String> headers = defaultHeaders();
        Map<String, Object> params = new HashMap<>();
        params.put("s", query.trim());
        params.put("type", 1);
        params.put("offset", 0);
        params.put("limit", 20);
        params.put("total", true);

        String body = buildFormBody(params);
        JsonObject root = postJson(BASE_URL + "/api/cloudsearch/pc/", body, headers);
        if (root == null || !root.has("result")) {
            return Collections.emptyList();
        }
        JsonObject resultObj = root.getAsJsonObject("result");
        if (!resultObj.has("songs")) {
            return Collections.emptyList();
        }
        JsonArray songs = resultObj.getAsJsonArray("songs");
        List<NeteaseSearchResult> results = new ArrayList<>();
        for (JsonElement element : songs) {
            JsonObject song = element.getAsJsonObject();
            String id = getString(song, "id");
            String title = getString(song, "name");
            int duration = song.has("dt") ? song.get("dt").getAsInt() / 1000 : 0;
            int fee = song.has("fee") ? song.get("fee").getAsInt() : 0;
            List<String> artists = new ArrayList<>();
            if (song.has("ar")) {
                for (JsonElement ar : song.getAsJsonArray("ar")) {
                    artists.add(getString(ar.getAsJsonObject(), "name"));
                }
            }
            String albumName = "";
            String picUrl = "";
            if (song.has("al")) {
                JsonObject album = song.getAsJsonObject("al");
                albumName = getString(album, "name");
                picUrl = getString(album, "picUrl");
            }
            results.add(new NeteaseSearchResult(id, title, String.join("/", artists), duration, albumName, picUrl, fee));
        }
        return results;
    }

    public static String resolveStreamUrl(String songId) throws IOException {
        String url = BASE_URL + "/api/song/enhance/player/url/v1?encodeType=mp3&ids=[" + songId + "]&level=standard";
        JsonObject root = getJson(url, defaultHeaders());
        if (root == null || !root.has("data")) {
            throw new IOException("No stream URL for NetEase song " + songId);
        }
        JsonArray data = root.getAsJsonArray("data");
        if (data.isEmpty()) {
            throw new IOException("Empty stream data for NetEase song " + songId);
        }
        JsonObject first = data.get(0).getAsJsonObject();
        if (!first.has("url") || first.get("url").isJsonNull()) {
            throw new IOException("NetEase song " + songId + " is not available (no copyright or VIP-only)");
        }
        return first.get("url").getAsString();
    }

    public static byte[] downloadStreamBytes(String songId) throws IOException {
        String streamUrl = resolveStreamUrl(songId);
        HttpURLConnection connection = (HttpURLConnection) URI.create(streamUrl).toURL().openConnection();
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Referer", "https://music.163.com");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        try (InputStream stream = connection.getInputStream()) {
            return stream.readAllBytes();
        } finally {
            connection.disconnect();
        }
    }

    public static byte[] downloadAlbumCoverBytes(String picUrl) throws IOException {
        if (picUrl == null || picUrl.isBlank()) {
            throw new IOException("Missing album cover URL");
        }
        HttpURLConnection connection = (HttpURLConnection) URI.create(picUrl).toURL().openConnection();
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Referer", "https://music.163.com");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        try (InputStream stream = connection.getInputStream()) {
            return stream.readAllBytes();
        } finally {
            connection.disconnect();
        }
    }
}
