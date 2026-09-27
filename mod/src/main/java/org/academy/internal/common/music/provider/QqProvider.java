package org.academy.internal.common.music.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.academy.internal.common.music.SharedTrackEntry;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.*;

/**
 * QQ 音乐提供者的公共实现，客户端本地与服务器共享账号解析共用喵。
 */
public final class QqProvider implements MusicProviderApi {
    public static final String NAME = "qq";
    private static final String MUSICU_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg";
    private static final String DEFAULT_SIP = "http://ws.stream.qqmusic.qq.com/";
    private static final int CODE_NO_PERMISSION = 104009;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String referer() {
        return "https://y.qq.com/";
    }

    private Map<String, String> headers(ProviderCredential credential) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", userAgent());
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.3,en;q=0.2");
        headers.put("Content-Type", "application/json;charset=utf-8");
        headers.put("Referer", "https://y.qq.com/");
        var cookie = credential.qqCookieString();
        if (!cookie.isBlank()) {
            headers.put("Cookie", cookie);
        }
        return headers;
    }

    @Override
    public List<ProviderSearchResult> search(String query, ProviderCredential credential) throws IOException {
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

        var root = HttpUtil.postJson(MUSICU_URL, request.toString(), headers(credential)).json();
        var data = requireReqData(root, "req");
        if (data == null
                || !data.has("body")
                || !data.getAsJsonObject("body").has("song")
                || !data.getAsJsonObject("body").getAsJsonObject("song").has("list")) {
            return Collections.emptyList();
        }
        var list = data.getAsJsonObject("body").getAsJsonObject("song").getAsJsonArray("list");
        List<ProviderSearchResult> results = new ArrayList<>();
        for (var element : list) {
            var song = element.getAsJsonObject();
            results.add(new ProviderSearchResult(
                    getString(song, "mid"),
                    getString(song, "name"),
                    joinSingers(song),
                    0,
                    isVipSong(song),
                    ""
            ));
        }
        return results;
    }

    @Override
    public TrackCandidate resolveTrack(String trackId, ProviderCredential credential) throws IOException {
        if (trackId == null || trackId.isBlank()) {
            throw new IOException("Missing QQ music track id");
        }
        var trackInfo = getTrackInfo(trackId, credential);
        var mediaMid = trackInfo.mediaMid().isBlank() ? trackId : trackInfo.mediaMid();
        var filenames = buildSupportedFilenames(
                mediaMid,
                trackInfo.size128Mp3(),
                trackInfo.size320Mp3(),
                trackInfo.sizeFlac()
        );
        var data = requestVkeyData(trackId, filenames, credential);
        var streamUrls = resolveStreamUrls(data);
        if (streamUrls.isEmpty()) {
            throw new IOException(diagnoseNoSource(
                    trackInfo.vip(),
                    credential,
                    data == null ? 0 : getInt(data, "code", 0)
            ));
        }
        return new TrackCandidate(streamUrls, "");
    }

    public TrackInfo getTrackInfo(String mid, ProviderCredential credential) throws IOException {
        var uin = effectiveUin(credential);
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

        var root = HttpUtil.postJson(MUSICU_URL, body.toString(), headers(credential)).json();
        var data = requireReqData(root, "req_1");
        if (data == null || !data.has("track_info")) {
            throw new IOException("QQ 音乐未找到歌曲信息");
        }
        var trackInfo = data.getAsJsonObject("track_info");
        var interval = trackInfo.has("interval") ? trackInfo.get("interval").getAsInt() : 0;
        var mediaMid = "";
        if (trackInfo.has("file") && trackInfo.getAsJsonObject("file").has("media_mid")) {
            mediaMid = getString(trackInfo.getAsJsonObject("file"), "media_mid");
        }
        var file = trackInfo.has("file") ? trackInfo.getAsJsonObject("file") : new JsonObject();
        var albumMid = "";
        if (trackInfo.has("album") && trackInfo.getAsJsonObject("album").has("mid")) {
            albumMid = getString(trackInfo.getAsJsonObject("album"), "mid");
        }
        return new TrackInfo(
                getString(trackInfo, "name"),
                joinSingers(trackInfo),
                interval,
                mediaMid,
                albumMid,
                isVipSong(trackInfo),
                getLong(file, "size_128mp3"),
                getLong(file, "size_320mp3"),
                getLong(file, "size_flac")
        );
    }

    public String resolveAlbumMid(String mid, ProviderCredential credential) throws IOException {
        return getTrackInfo(mid, credential).albumMid();
    }

    /**
     * 拉取 QQ 歌单详情（服务器预设歌单导入用），需要带凭证请求喵。
     */
    @Override
    public List<SharedTrackEntry> importPlaylist(
            String dissid,
            ProviderCredential credential
    ) throws IOException {
        var uin = effectiveUin(credential);
        var comm = new JsonObject();
        comm.addProperty("uin", uin);
        comm.addProperty("format", "json");
        comm.addProperty("ct", 24);
        comm.addProperty("cv", 0);

        var param = new JsonObject();
        param.addProperty("disstid", Long.parseLong(dissid));
        param.addProperty("enc_host_uin", "");
        param.addProperty("tag", 1);
        param.addProperty("userinfo", 1);
        param.addProperty("song_begin", 0);
        param.addProperty("song_num", 500);

        var req = new JsonObject();
        req.addProperty("method", "GetPlaylistByDissId");
        req.addProperty("module", "music.pf_song_box_playlist_svr");
        req.add("param", param);

        var body = new JsonObject();
        body.add("req_1", req);
        body.add("comm", comm);

        var root = HttpUtil.postJson(MUSICU_URL, body.toString(), headers(credential)).json();
        var data = requireReqData(root, "req_1");
        if (data == null || !data.has("dirinfo") || !data.has("songlist")) {
            throw new IOException("QQ 音乐歌单 " + dissid + " 未找到或无法访问");
        }
        List<SharedTrackEntry> entries = new ArrayList<>();
        for (var element : data.getAsJsonArray("songlist")) {
            var song = element.getAsJsonObject();
            entries.add(new SharedTrackEntry(
                    NAME,
                    getString(song, "mid"),
                    getString(song, "name"),
                    joinSingers(song),
                    song.has("interval") ? song.get("interval").getAsInt() : 0,
                    isVipSong(song),
                    ""
            ));
        }
        return entries;
    }

    private JsonObject requestVkeyData(
            String songMid,
            List<String> filenames,
            ProviderCredential credential
    ) throws IOException {
        var filenameList = new JsonArray();
        var songMidList = new JsonArray();
        var songTypeList = new JsonArray();
        for (var filename : filenames) {
            filenameList.add(filename);
            songMidList.add(songMid);
            songTypeList.add(0);
        }

        var uin = effectiveUin(credential);

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

        var root = HttpUtil.postJson(MUSICU_URL, body.toString(), headers(credential)).json();
        var data = requireReqData(root, "req_1");
        if (data == null) {
            return new JsonObject();
        }
        var dataCode = getInt(data, "code", 0);
        if (dataCode != 0 && dataCode != CODE_NO_PERMISSION) {
            throw new IOException("QQ 音乐接口返回错误（code=" + dataCode + "）");
        }
        return data;
    }

    private @Nullable JsonObject requireReqData(JsonObject root, String moduleKey) throws IOException {
        if (root == null) {
            throw new IOException("QQ 音乐接口响应为空");
        }
        var rootCode = getInt(root, "code", 0);
        if (rootCode != 0) {
            throw new IOException("QQ 音乐接口返回错误（code=" + rootCode + "）");
        }
        if (!root.has(moduleKey) || !root.get(moduleKey).isJsonObject()) {
            throw new IOException("QQ 音乐接口响应缺少 " + moduleKey + " 字段");
        }
        var module = root.getAsJsonObject(moduleKey);
        var moduleCode = getInt(module, "code", 0);
        if (moduleCode != 0) {
            if (moduleCode == 2001) {
                throw new IOException("QQ 音乐请求过于频繁，请稍后重试");
            }
            if (moduleCode == CODE_NO_PERMISSION) {
                var permissionData = new JsonObject();
                permissionData.addProperty("code", moduleCode);
                return permissionData;
            }
            throw new IOException("QQ 音乐接口返回错误（code=" + moduleCode + "）");
        }
        if (!module.has("data") || !module.get("data").isJsonObject()) {
            return null;
        }
        return module.getAsJsonObject("data");
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

    public static List<String> buildSupportedFilenames(
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

    private static String effectiveUin(ProviderCredential credential) {
        return credential.hasQqCredential() ? credential.qqMusicId() : "0";
    }

    public static String diagnoseNoSource(boolean vip, ProviderCredential credential, int apiCode) {
        if (apiCode == CODE_NO_PERMISSION) {
            if (vip) {
                return diagnoseVipWithoutSource(credential);
            }
            return "该歌曲为付费/VIP 曲目，当前账号无播放权限";
        }
        if (vip) {
            return diagnoseVipWithoutSource(credential);
        }
        if (apiCode != 0) {
            return "QQ 音乐未返回可播放的音频源（code=" + apiCode + "）";
        }
        return "QQ 音乐未返回可播放的音频源";
    }

    private static String diagnoseVipWithoutSource(ProviderCredential credential) {
        if (!credential.hasQqCredential()) {
            return "付费歌曲需登录 QQ 音乐账号（VIP）后才能播放";
        }
        if (credential.isQqExpired()) {
            return "QQ 音乐登录已过期，请重新登录后再播放付费歌曲";
        }
        return "付费歌曲暂时无法播放，请确认账号具备 VIP 权限";
    }

    private static boolean isVipSong(JsonObject song) {
        return song.has("pay") && song.getAsJsonObject("pay").has("pay_play")
                && song.getAsJsonObject("pay").get("pay_play").getAsInt() == 1;
    }

    private static String joinSingers(JsonObject song) {
        List<String> singers = new ArrayList<>();
        if (song.has("singer")) {
            for (var singerElement : song.getAsJsonArray("singer")) {
                singers.add(getString(singerElement.getAsJsonObject(), "name"));
            }
        }
        return String.join("/", singers);
    }

    private static long getLong(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return 0L;
        try {
            return object.get(key).getAsLong();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static int getInt(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String getString(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString()
                : "";
    }

    public record TrackInfo(
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
}
