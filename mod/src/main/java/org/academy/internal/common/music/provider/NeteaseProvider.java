package org.academy.internal.common.music.provider;

import com.google.gson.JsonObject;
import org.academy.internal.common.music.SharedTrackEntry;

import java.io.IOException;
import java.util.*;

/**
 * 网易云音乐提供者的公共实现，客户端本地与服务器共享账号解析共用喵。
 */
public final class NeteaseProvider implements MusicProviderApi {
    public static final String NAME = "netease";
    private static final String BASE_URL = "https://music.163.com";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.164 NeteaseMusicDesktop/3.1.6";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String userAgent() {
        return USER_AGENT;
    }

    @Override
    public String referer() {
        return "https://music.163.com";
    }

    private Map<String, String> headers(ProviderCredential credential) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Referer", BASE_URL);
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        if (credential.hasCookie()) {
            headers.put("Cookie", credential.cookie());
        }
        return headers;
    }

    @Override
    public List<ProviderSearchResult> search(String query, ProviderCredential credential) throws IOException {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("s", query.trim());
        params.put("type", 1);
        params.put("offset", 0);
        params.put("limit", 20);
        params.put("total", true);
        var root = HttpUtil.postJson(BASE_URL + "/api/cloudsearch/pc/", HttpUtil.formBody(params), headers(credential)).json();
        if (root == null || !root.has("result")) {
            return Collections.emptyList();
        }
        var resultObj = root.getAsJsonObject("result");
        if (!resultObj.has("songs")) {
            return Collections.emptyList();
        }
        var songs = resultObj.getAsJsonArray("songs");
        List<ProviderSearchResult> results = new ArrayList<>();
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
            var picUrl = "";
            if (song.has("al")) {
                picUrl = getString(song.getAsJsonObject("al"), "picUrl");
            }
            results.add(new ProviderSearchResult(
                    id, title, String.join("/", artists), duration, fee >= 1, picUrl
            ));
        }
        return results;
    }

    @Override
    public TrackCandidate resolveTrack(String trackId, ProviderCredential credential) throws IOException {
        if (trackId == null || trackId.isBlank()) {
            throw new IOException("Missing NetEase song id");
        }
        var url = BASE_URL + "/api/song/enhance/player/url/v1?encodeType=mp3&ids=[" + trackId + "]&level=standard";
        var root = HttpUtil.getJson(url, headers(credential)).json();
        if (root == null || !root.has("data")) {
            throw new IOException("No stream URL for NetEase song " + trackId);
        }
        var data = root.getAsJsonArray("data");
        if (data.isEmpty()) {
            throw new IOException("Empty stream data for NetEase song " + trackId);
        }
        var first = data.get(0).getAsJsonObject();
        if (!first.has("url") || first.get("url").isJsonNull()) {
            throw new IOException("NetEase song " + trackId + " is not available (no copyright or VIP-only)");
        }
        return new TrackCandidate(List.of(first.get("url").getAsString()), "");
    }

    /**
     * 拉取网易歌单详情（服务器预设歌单导入用），需要带凭证请求喵。
     */
    @Override
    public List<SharedTrackEntry> importPlaylist(String playlistId, ProviderCredential credential) throws IOException {
        var headers = headers(credential);
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        Map<String, Object> params = new HashMap<>();
        params.put("id", playlistId);
        params.put("n", 1000);
        var root = HttpUtil.postJson(
                BASE_URL + "/api/v6/playlist/detail",
                HttpUtil.formBody(params),
                headers
        ).json();
        if (root == null || !root.has("playlist") || !root.getAsJsonObject("playlist").has("trackIds")) {
            throw new IOException("NetEase playlist " + playlistId + " not found");
        }
        var trackIds = root.getAsJsonObject("playlist").getAsJsonArray("trackIds");
        List<String> ids = new ArrayList<>();
        for (var element : trackIds) {
            var idObject = element.getAsJsonObject();
            if (idObject.has("id")) ids.add(String.valueOf(idObject.get("id").getAsLong()));
        }
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<SharedTrackEntry> entries = new ArrayList<>();
        for (var chunkStart = 0; chunkStart < ids.size(); chunkStart += 100) {
            var chunk = ids.subList(chunkStart, Math.min(ids.size(), chunkStart + 100));
            var detailRoot = HttpUtil.postJson(
                    BASE_URL + "/api/v3/song/detail",
                    HttpUtil.formBody(Map.of("c", "[" + String.join(",", chunk.stream().map(id -> "{\"id\":" + id + "}").toList()) + "]")),
                    headers
            ).json();
            if (detailRoot == null || !detailRoot.has("songs")) continue;
            for (var element : detailRoot.getAsJsonArray("songs")) {
                var song = element.getAsJsonObject();
                var fee = song.has("fee") ? song.get("fee").getAsInt() : 0;
                List<String> artists = new ArrayList<>();
                if (song.has("ar")) {
                    for (var ar : song.getAsJsonArray("ar")) {
                        artists.add(getString(ar.getAsJsonObject(), "name"));
                    }
                }
                var picUrl = "";
                if (song.has("al")) {
                    picUrl = getString(song.getAsJsonObject("al"), "picUrl");
                }
                entries.add(new SharedTrackEntry(
                        NAME,
                        getString(song, "id"),
                        getString(song, "name"),
                        String.join("/", artists),
                        song.has("dt") ? song.get("dt").getAsInt() / 1000 : 0,
                        fee >= 1,
                        picUrl
                ));
            }
        }
        return entries;
    }

    private static String getString(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString()
                : "";
    }
}
