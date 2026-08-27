package org.academy.internal.client.app.music.qq;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.academy.AcademyCraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public final class QqLoginService {
    private static final String APPID = "716027609";
    private static final String THIRD_APPID = "100497308";
    private static final String REDIRECT_URI = "https://y.qq.com/wk_v17/common_login.html?type=QQ&&redirect=";
    private static final String QR_SHOW_URL = "https://xui.ptlogin2.qq.com/ssl/ptqrshow";
    private static final String QR_LOGIN_URL = "https://xui.ptlogin2.qq.com/ssl/ptqrlogin";
    private static final String AUTHORIZE_URL = "https://graph.qq.com/oauth2.0/authorize";
    private static final String MUSICU_URL = "https://u6.y.qq.com/cgi-bin/musicu.fcg";
    private static final String LOGIN_JUMP_URL = "https://graph.qq.com/oauth2.0/login_jump";
    private static final String XLOGIN_URL = "https://xui.ptlogin2.qq.com/cgi-bin/xlogin";
    private static final String CONCERTO_REDIRECT_URI = "https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/";
    private static final String LOCAL_PTLOGIN_HOST = "https://localhost.ptlogin2.qq.com";
    private static final String PTLOGIN_JUMP_URL = "https://ssl.ptlogin2.qq.com/jump";
    private static final Pattern PTUI_CB = Pattern.compile("ptuiCB\\('(\\d+)','[^']*','([^']*)','[^']*','([^']*)'");
    private static final Pattern PTUI_REDIRECT_PATTERN = Pattern.compile("ptui(?:_qlogin)?CB\\('[^']*','[^']*','([^']*)'");
    private static final Pattern CODE_PATTERN = Pattern.compile("code=([^&]+)");
    private static final Pattern CALLBACK_URL_PATTERN = Pattern.compile("https://y\\.qq\\.com/wk_v17/common_login\\.html[^\\s\"'<>]+");
    private static final Pattern CALLBACK_CODE_PATTERN = Pattern.compile("https://y\\.qq\\.com/wk_v17/common_login\\.html[^\\s\"'<>]*[?&]code=([A-Za-z0-9]+)");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>]+");
    private static final Pattern JS_URL_PATTERN = Pattern.compile("(?:location\\.(?:href|replace|assign)|top\\.location|window\\.location)\\s*[=\\(]\\s*[\"']([^\"']+)[\"']");
    private static final Pattern META_REFRESH_PATTERN = Pattern.compile("url=([^\"'>\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("(\\d+)");
    private static final Map<String, String> AUTH_SESSION_COOKIES = new LinkedHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            var thread = new Thread(runnable, "academy-qq-login-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    });
    private static volatile String lastStatusText = "QQ 音乐：未登录";

    private QqLoginService() {
    }

    public static String getLastStatusText() {
        return lastStatusText;
    }

    public static CompletableFuture<QrCodeSession> fetchQrCode() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                updateStatus("QQ 音乐：阶段 1/4 获取二维码");
                clearAuthSessionCookies();
                var urlBuilder = QR_SHOW_URL + "?appid=" + APPID +
                        "&e=2&l=M&s=3&d=72&v=4&t=0.787&daid=383" +
                        "&pt_3rd_aid=" + THIRD_APPID +
                        "&u1=" + URLEncoder.encode(LOGIN_JUMP_URL, StandardCharsets.UTF_8);

                var connection = (HttpURLConnection) new URL(urlBuilder).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();

                String qrsig = null;
                for (var entry : connection.getHeaderFields().entrySet()) {
                    if (!"Set-Cookie".equalsIgnoreCase(entry.getKey())) {
                        continue;
                    }
                    for (var cookieStr : entry.getValue()) {
                        var value = extractCookieValue(cookieStr, "qrsig");
                        if (value != null) {
                            qrsig = value;
                            break;
                        }
                    }
                    if (qrsig != null) {
                        break;
                    }
                }

                var imageData = connection.getInputStream().readAllBytes();
                connection.disconnect();
                if (qrsig == null || qrsig.isBlank()) {
                    AcademyCraft.LOGGER.error("QQ music login stage fetchQrCode failed: missing qrsig");
                    updateStatus("QQ 音乐：二维码缺少 qrsig");
                    throw new IOException("Failed to obtain qrsig");
                }
                AcademyCraft.LOGGER.info("QQ music login stage fetchQrCode ok, hasQrsig={}", true);
                updateStatus("QQ 音乐：请使用手机 QQ 扫码");
                return new QrCodeSession(imageData, qrsig);
            } catch (Exception e) {
                updateStatus("QQ 音乐：阶段 1/4 获取二维码失败");
                throw new RuntimeException("Failed to fetch QQ music login QR code", e);
            }
        }, EXECUTOR);
    }

    public static CompletableFuture<LoginState> pollLogin(String qrsig) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (qrsig == null || qrsig.isBlank()) {
                    updateStatus("QQ 音乐：阶段 2/4 轮询失败，qrsig 为空");
                    return LoginState.FAILED;
                }
                updateStatus("QQ 音乐：阶段 2/4 等待扫码确认");
                var ptqrtoken = calculatePtqrtoken(qrsig);
                var urlBuilder = QR_LOGIN_URL + "?u1=" + URLEncoder.encode(LOGIN_JUMP_URL, StandardCharsets.UTF_8) +
                        "&ptqrtoken=" + ptqrtoken +
                        "&ptredirect=0&h=1&t=1&g=1&from_ui=1&ptlang=2052" +
                        "&js_ver=25072815&js_type=1&login_sig=&pt_uistyle=40" +
                        "&aid=" + APPID +
                        "&daid=383" +
                        "&pt_3rd_aid=" + THIRD_APPID;

                var connection = (HttpURLConnection) new URL(urlBuilder).openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Cookie", "qrsig=" + qrsig);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                var responseCode = connection.getResponseCode();
                var body = readResponse(connection);
                connection.disconnect();
                AcademyCraft.LOGGER.debug("QQ music login stage ptqrlogin code={}, body={}", responseCode, summarize(body));

                var matcher = PTUI_CB.matcher(body);
                if (!matcher.find()) {
                    AcademyCraft.LOGGER.error("QQ music login stage ptqrlogin failed: ptuiCB missing, body={}", summarize(body));
                    updateStatus("QQ 音乐：阶段 2/4 轮询响应异常");
                    return LoginState.WAITING_SCAN;
                }
                var code = matcher.group(1);
                if ("65".equals(code)) {
                    updateStatus("QQ 音乐：二维码已过期");
                    return LoginState.QR_EXPIRED;
                }
                if (!"0".equals(code)) {
                    updateStatus("QQ 音乐：阶段 2/4 等待手机确认");
                    return LoginState.WAITING_SCAN;
                }
                var checkSigUrl = matcher.group(2);
                if (checkSigUrl == null || checkSigUrl.isBlank()) {
                    checkSigUrl = matcher.group(3);
                }
                AcademyCraft.LOGGER.info("QQ music login stage ptqrlogin ok, checkSigUrl={}", checkSigUrl);
                return processLoginSuccess(checkSigUrl);
            } catch (Exception e) {
                AcademyCraft.LOGGER.error("QQ music login poll failed", e);
                updateStatus("QQ 音乐：阶段 2/4 轮询失败");
                return LoginState.FAILED;
            }
        }, EXECUTOR);
    }

    private static LoginState processLoginSuccess(String checkSigUrl) {
        try {
            updateStatus("QQ 音乐：阶段 3/4 获取授权票据");
            var connection = (HttpURLConnection) new URL(checkSigUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.connect();
            var responseCode = connection.getResponseCode();

            String uin = null;
            String ptOauthToken = null;
            String pSkey = null;
            for (var entry : connection.getHeaderFields().entrySet()) {
                if (!"Set-Cookie".equalsIgnoreCase(entry.getKey())) {
                    continue;
                }
                for (var cookieStr : entry.getValue()) {
                    String value;
                    if ((value = extractCookieValue(cookieStr, "pt2gguin")) != null) {
                        uin = value;
                    }
                    if ((value = extractCookieValue(cookieStr, "pt_oauth_token")) != null) {
                        ptOauthToken = value;
                    }
                    if ((value = extractCookieValue(cookieStr, "p_skey")) != null) {
                        pSkey = value;
                    }
                }
            }
            captureCookies(connection, AUTH_SESSION_COOKIES);
            var checkSigRedirect = connection.getHeaderField("Location");
            connection.disconnect();
            AcademyCraft.LOGGER.info("QQ music login stage checkSig code={}, hasUin={}, hasOauthToken={}, hasPSkey={}, hasLocation={}, cookies={}",
                    responseCode, uin != null, ptOauthToken != null, pSkey != null, checkSigRedirect != null, AUTH_SESSION_COOKIES.keySet());

            if (uin == null || ptOauthToken == null || pSkey == null) {
                AcademyCraft.LOGGER.error("QQ music login stage checkSig failed: missing cookies");
                updateStatus("QQ 音乐：阶段 3/4 未取到授权 Cookie");
                return LoginState.FAILED;
            }
            followAuthorizeSessionRedirects(checkSigRedirect);

            var authCode = authorizeViaConcertoFlow();
            if (authCode == null || authCode.isBlank()) {
                authCode = requestXloginCode();
            }
            if (authCode == null || authCode.isBlank()) {
                authCode = requestLocalJumpCode(uin);
            }
            if (authCode == null || authCode.isBlank()) {
                authCode = requestLoginJumpCode();
            }
            if (authCode == null || authCode.isBlank()) {
                authCode = authorize(uin, ptOauthToken, pSkey);
            }
            if (authCode == null) {
                updateStatus("QQ 音乐：阶段 3/4 未取到授权 code");
                return LoginState.FAILED;
            }
            updateStatus("QQ 音乐：阶段 4/4 登录 QQ 音乐");
            var credential = qqConnectLoginServer(authCode);
            if (credential == null) {
                updateStatus("QQ 音乐：阶段 4/4 登录凭据交换失败");
                return LoginState.FAILED;
            }
            QqCredentialManager.save(credential);
            AcademyCraft.LOGGER.info("QQ music login success, musicId={}, expiresIn={}",
                    credential.getMusicId(), credential.getKeyExpiresIn());
            updateStatus("QQ 音乐：已登录");
            return LoginState.SUCCESS;
        } catch (Exception e) {
            AcademyCraft.LOGGER.error("QQ music login processing failed", e);
            updateStatus("QQ 音乐：阶段 3/4 或 4/4 处理失败");
            return LoginState.FAILED;
        }
    }

    private static String authorize(String uin, String ptOauthToken, String pSkey) throws IOException {
        updateStatus("QQ 音乐：阶段 3/4 请求 authorize");
        var gtk = calculateGtk(pSkey);
        Map<String, String> formData = new LinkedHashMap<>();
        formData.put("response_type", "code");
        formData.put("client_id", THIRD_APPID);
        formData.put("redirect_uri", REDIRECT_URI);
        formData.put("scope", "get_user_info");
        formData.put("state", "y_new.top.pop.logout");
        formData.put("switch", "");
        formData.put("from_ptlogin", "1");
        formData.put("src", "1");
        formData.put("update_auth", "1");
        formData.put("openapi", "1010");
        formData.put("g_tk", String.valueOf(gtk));
        formData.put("auth_time", String.valueOf(System.currentTimeMillis() / 1000));

        var formBody = new StringBuilder();
        for (var entry : formData.entrySet()) {
            if (formBody.length() > 0) {
                formBody.append('&');
            }
            formBody.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            formBody.append('=');
            formBody.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }

        var connection = (HttpURLConnection) new URL(AUTHORIZE_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.setInstanceFollowRedirects(false);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        connection.setRequestProperty("Referer", LOGIN_JUMP_URL);
        connection.setRequestProperty("Cookie", buildCookieHeader(
                "p_uin=" + uin,
                "pt_oauth_token=" + ptOauthToken,
                "p_skey=" + pSkey,
                buildCookieHeader(AUTH_SESSION_COOKIES)
        ));
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        try (var outputStream = connection.getOutputStream()) {
            outputStream.write(formBody.toString().getBytes(StandardCharsets.UTF_8));
        }
        var responseCode = connection.getResponseCode();
        var location = connection.getHeaderField("Location");
        var body = location == null ? safeReadResponse(connection) : "";
        var refresh = connection.getHeaderField("Refresh");
        captureCookies(connection, AUTH_SESSION_COOKIES);
        connection.disconnect();
        AcademyCraft.LOGGER.info("QQ music login stage authorize code={}, hasLocation={}, hasRefresh={}, cookies={}, body={}",
                responseCode, location != null, refresh != null, AUTH_SESSION_COOKIES.keySet(), summarize(body));
        if (location == null) {
            AcademyCraft.LOGGER.error("QQ music login stage authorize failed: no redirect location, body={}", summarize(body));
            return null;
        }
        var matcher = CODE_PATTERN.matcher(location);
        if (matcher.find()) {
            var authCode = matcher.group(1);
            AcademyCraft.LOGGER.info("QQ music login stage authorize ok: codeLength={}", authCode.length());
            return authCode;
        }
        var authCode = followAuthorizeCodeRedirects(location);
        if (authCode == null || authCode.isBlank()) {
            AcademyCraft.LOGGER.error("QQ music login stage authorize failed: no code found after follow, location={}, refresh={}, body={}",
                    location, refresh, summarize(body));
            return null;
        }
        AcademyCraft.LOGGER.info("QQ music login stage authorize ok after follow: codeLength={}", authCode.length());
        return authCode;
    }

    private static String authorizeViaConcertoFlow() {
        try {
            updateStatus("QQ 音乐：阶段 3/4 请求 authorize");
            var pSkey = AUTH_SESSION_COOKIES.get("p_skey");
            if (pSkey == null || pSkey.isBlank()) {
                pSkey = AUTH_SESSION_COOKIES.getOrDefault("p_skey", "");
            }
            var gtk = calculateGtk(pSkey);
            var ui = AUTH_SESSION_COOKIES.computeIfAbsent("ui", ignored -> UUID.randomUUID().toString().toUpperCase());
            AUTH_SESSION_COOKIES.put("gtk", String.valueOf(gtk));

            var formBody = new StringBuilder();
            appendFormField(formBody, "response_type", "code");
            appendFormField(formBody, "client_id", THIRD_APPID);
            appendFormField(formBody, "redirect_uri", CONCERTO_REDIRECT_URI);
            appendFormField(formBody, "scope", "get_user_info,get_app_friends");
            appendFormField(formBody, "state", "state");
            appendFormField(formBody, "switch", "");
            appendFormField(formBody, "from_ptlogin", "1");
            appendFormField(formBody, "src", "1");
            appendFormField(formBody, "update_auth", "1");
            appendFormField(formBody, "openapi", "80901010");
            appendFormField(formBody, "g_tk", String.valueOf(gtk));
            appendFormField(formBody, "auth_time", String.valueOf(System.currentTimeMillis() / 1000));
            appendFormField(formBody, "ui", ui);

            var connection = (HttpURLConnection) new URL(AUTHORIZE_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Referer", "https://graph.qq.com");
            var cookieHeader = buildCookieHeader(AUTH_SESSION_COOKIES);
            if (!cookieHeader.isBlank()) {
                connection.setRequestProperty("Cookie", cookieHeader);
            }
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            try (var outputStream = connection.getOutputStream()) {
                outputStream.write(formBody.toString().getBytes(StandardCharsets.UTF_8));
            }
            var responseCode = connection.getResponseCode();
            var location = connection.getHeaderField("Location");
            var body = location == null ? safeReadResponse(connection) : "";
            captureCookies(connection, AUTH_SESSION_COOKIES);
            connection.disconnect();

            var authCode = extractPortalCode(location, body);
            AcademyCraft.LOGGER.info("QQ music login stage concerto-authorize code={}, nextLocation={}, cookies={}, body={}",
                    responseCode, location, AUTH_SESSION_COOKIES.keySet(), summarize(body));
            if (authCode == null || authCode.isBlank()) {
                return null;
            }
            AcademyCraft.LOGGER.info("QQ music login stage concerto-authorize ok: codeLength={}", authCode.length());
            return authCode;
        } catch (Exception e) {
            AcademyCraft.LOGGER.error("QQ music login stage concerto-authorize failed", e);
            return null;
        }
    }

    private static QqCredential qqConnectLoginServer(String code) {
        HttpURLConnection connection = null;
        try {
            var comm = new JsonObject();
            comm.addProperty("g_tk", Long.parseLong(AUTH_SESSION_COOKIES.getOrDefault("gtk", "5381")));
            comm.addProperty("platform", "yqq");
            comm.addProperty("ct", 24);
            comm.addProperty("cv", 0);

            var param = new JsonObject();
            param.addProperty("code", code);

            var req = new JsonObject();
            req.addProperty("module", "QQConnectLogin.LoginServer");
            req.addProperty("method", "QQLogin");
            req.add("param", param);

            var body = new JsonObject();
            body.add("comm", comm);
            body.add("req", req);

            connection = (HttpURLConnection) new URL(MUSICU_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Content-Type", "application/json;charset=utf-8");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "application/json, text/plain, */*");
            connection.setRequestProperty("Origin", "https://y.qq.com");
            connection.setRequestProperty("Referer", "https://y.qq.com/");
            var cookieHeader = buildCookieHeader(AUTH_SESSION_COOKIES);
            if (!cookieHeader.isBlank()) {
                connection.setRequestProperty("Cookie", cookieHeader);
            }
            try (var outputStream = connection.getOutputStream()) {
                outputStream.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            var responseCode = connection.getResponseCode();
            var responseBody = readResponse(connection);
            captureCookies(connection, AUTH_SESSION_COOKIES);
            var result = parseLoginExchangeResponse(responseBody, AUTH_SESSION_COOKIES);
            AcademyCraft.LOGGER.info(
                    "QQ music login stage QQConnectLogin httpCode={}, rootCode={}, serviceCode={}, responseKey={}, credentialSource={}, cookies={}",
                    responseCode, result.rootCode(), result.serviceCode(), result.responseKey(),
                    result.credentialSource(), AUTH_SESSION_COOKIES.keySet());
            if (result.credential() == null) {
                AcademyCraft.LOGGER.error(
                        "QQ music login stage QQConnectLogin failed: rootCode={}, serviceCode={}, responseKey={}",
                        result.rootCode(), result.serviceCode(), result.responseKey());
            }
            return result.credential();
        } catch (Exception e) {
            AcademyCraft.LOGGER.error("QQ music login stage QQConnectLogin failed", e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static LoginExchangeResult parseLoginExchangeResponse(String responseBody, Map<String, String> cookies) {
        var rootCode = Integer.MIN_VALUE;
        var serviceCode = Integer.MIN_VALUE;
        var responseKey = "";
        JsonObject data = null;
        try {
            var parsed = JsonParser.parseString(responseBody == null ? "" : responseBody);
            if (parsed.isJsonObject()) {
                var root = parsed.getAsJsonObject();
                rootCode = getInt(root, "code", Integer.MIN_VALUE);
                var serviceResponse = getObject(root, "req");
                if (serviceResponse != null) {
                    responseKey = "req";
                } else {
                    serviceResponse = getObject(root, "music.login.LoginServer.Login");
                    if (serviceResponse != null) {
                        responseKey = "music.login.LoginServer.Login";
                    }
                }
                if (serviceResponse != null) {
                    serviceCode = getInt(serviceResponse, "code", Integer.MIN_VALUE);
                    if (serviceCode == 0) {
                        data = getObject(serviceResponse, "data");
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // A cookie-only response is still valid for older QQ Music login servers.
        }

        var jsonCredential = credentialFromData(data, cookies);
        if (jsonCredential != null) {
            return new LoginExchangeResult(rootCode, serviceCode, responseKey, CredentialSource.JSON, jsonCredential);
        }
        var cookieCredential = credentialFromCookies(cookies);
        if (cookieCredential != null) {
            return new LoginExchangeResult(rootCode, serviceCode, responseKey, CredentialSource.COOKIE, cookieCredential);
        }
        return new LoginExchangeResult(rootCode, serviceCode, responseKey, CredentialSource.NONE, null);
    }

    private static QqCredential credentialFromData(JsonObject data, Map<String, String> cookies) {
        if (data == null) {
            return null;
        }
        var musicId = normalizeUin(firstNonBlank(
                getString(data, "str_musicid"),
                getString(data, "musicid"),
                cookies == null ? null : cookies.get("uin"),
                cookies == null ? null : cookies.get("wxuin")
        ));
        if ("0".equals(musicId)) {
            musicId = null;
        }
        var musicKey = firstNonBlank(
                getString(data, "musickey"),
                getString(data, "musicKey"),
                cookies == null ? null : cookies.get("qm_keyst"),
                cookies == null ? null : cookies.get("qqmusic_key")
        );
        var credential = new QqCredential(
                musicId,
                musicKey,
                getLong(data, "keyExpiresIn", 0L),
                getLong(data, "musickeyCreateTime", System.currentTimeMillis() / 1000),
                getString(data, "refresh_key"),
                getString(data, "refresh_token")
        );
        return credential.isValid() ? credential : null;
    }

    private static QqCredential credentialFromCookies(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return null;
        }
        var musicId = normalizeUin(firstNonBlank(cookies.get("uin"), cookies.get("wxuin")));
        var musicKey = firstNonBlank(cookies.get("qm_keyst"), cookies.get("qqmusic_key"));
        var credential = new QqCredential(
                musicId, musicKey, 0L, System.currentTimeMillis() / 1000, "", "");
        return credential.isValid() ? credential : null;
    }

    private static JsonObject getObject(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonObject()) {
            return null;
        }
        return object.getAsJsonObject(key);
    }

    private static String getString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        try {
            return object.get(key).getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static int getInt(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long getLong(JsonObject object, String key, long fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String readResponse(HttpURLConnection connection) throws IOException {
        var stream = connection.getResponseCode() >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            throw new IOException("Empty HTTP response body");
        }
        try (var bufferedReader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            var builder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

    private static void followAuthorizeSessionRedirects(String redirectUrl) {
        if (redirectUrl == null || redirectUrl.isBlank()) {
            AcademyCraft.LOGGER.warn("QQ music login stage session follow skipped: empty redirect url");
            return;
        }
        var currentUrl = redirectUrl;
        var referer = LOGIN_JUMP_URL;
        for (var step = 0; step < 5 && currentUrl != null && !currentUrl.isBlank(); step++) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(currentUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                if (referer != null && !referer.isBlank()) {
                    connection.setRequestProperty("Referer", referer);
                }
                var cookieHeader = buildCookieHeader(AUTH_SESSION_COOKIES);
                if (!cookieHeader.isBlank()) {
                    connection.setRequestProperty("Cookie", cookieHeader);
                }
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                var responseCode = connection.getResponseCode();
                captureCookies(connection, AUTH_SESSION_COOKIES);
                var location = connection.getHeaderField("Location");
                var body = responseCode >= 400 || location == null ? safeReadResponse(connection) : "";
                AcademyCraft.LOGGER.info("QQ music login stage session follow step={}, code={}, nextLocation={}, cookies={}, body={}",
                        step + 1, responseCode, location, AUTH_SESSION_COOKIES.keySet(), summarize(body));
                if (location == null || responseCode < 300 || responseCode >= 400) {
                    return;
                }
                referer = currentUrl;
                currentUrl = location;
            } catch (Exception e) {
                AcademyCraft.LOGGER.error("QQ music login stage session follow failed at url={}", currentUrl, e);
                return;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }

    private static String followAuthorizeCodeRedirects(String startUrl) {
        var currentUrl = startUrl;
        var referer = LOGIN_JUMP_URL;
        for (var step = 0; step < 5 && currentUrl != null && !currentUrl.isBlank(); step++) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(currentUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                if (referer != null && !referer.isBlank()) {
                    connection.setRequestProperty("Referer", referer);
                }
                var cookieHeader = buildCookieHeader(AUTH_SESSION_COOKIES);
                if (!cookieHeader.isBlank()) {
                    connection.setRequestProperty("Cookie", cookieHeader);
                }
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                var responseCode = connection.getResponseCode();
                var location = connection.getHeaderField("Location");
                var refresh = connection.getHeaderField("Refresh");
                var body = responseCode >= 400 || location == null ? safeReadResponse(connection) : "";
                captureCookies(connection, AUTH_SESSION_COOKIES);
                connection.disconnect();

                var foundCode = extractCode(location, refresh);
                if (foundCode == null || foundCode.isBlank()) {
                    foundCode = extractCallbackCode(body);
                }
                AcademyCraft.LOGGER.info("QQ music login stage authorize-follow step={}, code={}, nextLocation={}, hasRefresh={}, cookies={}, body={}",
                        step + 1, responseCode, location, refresh != null, AUTH_SESSION_COOKIES.keySet(), summarize(body));
                if (foundCode != null && !foundCode.isBlank()) {
                    return foundCode;
                }

                var nextUrl = location;
                if (nextUrl == null || nextUrl.isBlank()) {
                    nextUrl = extractNextUrl(body, refresh);
                }
                if (nextUrl == null || nextUrl.isBlank()) {
                    return null;
                }
                referer = currentUrl;
                currentUrl = nextUrl;
            } catch (Exception e) {
                AcademyCraft.LOGGER.error("QQ music login stage authorize-follow failed at url={}", currentUrl, e);
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        return null;
    }

    private static String requestLoginJumpCode() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(LOGIN_JUMP_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Referer", "https://graph.qq.com/");
            var cookieHeader = buildCookieHeader(AUTH_SESSION_COOKIES);
            if (!cookieHeader.isBlank()) {
                connection.setRequestProperty("Cookie", cookieHeader);
            }
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            var responseCode = connection.getResponseCode();
            var location = connection.getHeaderField("Location");
            var refresh = connection.getHeaderField("Refresh");
            var body = responseCode >= 400 || location == null ? safeReadResponse(connection) : "";
            captureCookies(connection, AUTH_SESSION_COOKIES);
            connection.disconnect();
            var authCode = extractCode(location, refresh);
            if (authCode == null || authCode.isBlank()) {
                authCode = extractCallbackCode(body);
            }
            AcademyCraft.LOGGER.info("QQ music login stage login_jump code={}, nextLocation={}, hasRefresh={}, cookies={}, body={}",
                    responseCode, location, refresh != null, AUTH_SESSION_COOKIES.keySet(), summarize(body));
            if (authCode != null && !authCode.isBlank()) {
                return authCode;
            }
            var nextUrl = location;
            if (nextUrl == null || nextUrl.isBlank()) {
                nextUrl = extractNextUrl(body, refresh);
            }
            if (nextUrl == null || nextUrl.isBlank()) {
                return null;
            }
            return followAuthorizeCodeRedirects(nextUrl);
        } catch (Exception e) {
            AcademyCraft.LOGGER.error("QQ music login stage login_jump failed", e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String requestLocalJumpCode(String uin) {
        var normalizedUin = normalizeUin(uin);
        var ptLocalToken = AUTH_SESSION_COOKIES.get("pt_local_token");
        if (normalizedUin == null || normalizedUin.isBlank() || ptLocalToken == null || ptLocalToken.isBlank()) {
            AcademyCraft.LOGGER.info("QQ music login stage local_jump skipped: hasUin={}, hasPtLocalToken={}",
                    normalizedUin != null && !normalizedUin.isBlank(), ptLocalToken != null && !ptLocalToken.isBlank());
            return null;
        }
        var session = fetchPtLocalSession(normalizedUin, ptLocalToken);
        if (session == null) {
            return null;
        }
        return requestPtloginJumpCode(normalizedUin, session);
    }

    private static PtLocalSession fetchPtLocalSession(String normalizedUin, String ptLocalToken) {
        List<Integer> ports = new ArrayList<>();
        for (var port = 4301; port <= 4309; port += 2) {
            ports.add(port);
        }
        for (int port : ports) {
            HttpURLConnection connection = null;
            try {
                var builder = LOCAL_PTLOGIN_HOST +
                        ':' + port +
                        "/pt_get_st?clientuin=" + normalizedUin +
                        "&r=" + Math.random() +
                        "&pt_local_tk=" + URLEncoder.encode(ptLocalToken, StandardCharsets.UTF_8) +
                        "&pt_aid=" + APPID +
                        "&daid=383" +
                        "&pt_3rd_aid=" + THIRD_APPID +
                        "&u1=" + URLEncoder.encode(LOGIN_JUMP_URL, StandardCharsets.UTF_8) +
                        "&callback=__jp0";
                connection = (HttpURLConnection) new URL(builder).openConnection();
                connection.setRequestMethod("GET");
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                connection.setRequestProperty("Referer", buildXloginUrl());
                connection.setRequestProperty("Accept", "*/*");
                connection.setConnectTimeout(2000);
                connection.setReadTimeout(2000);
                var body = readResponse(connection);
                var localTk = extractNumericField(body, "pt_local_tk");
                var keyIndex = extractNumericField(body, "keyindex");
                AcademyCraft.LOGGER.info("QQ music login stage pt_get_st port={}, hasLocalTk={}, keyIndex={}, body={}",
                        port, localTk != null, keyIndex, summarize(body));
                if (localTk != null && keyIndex != null) {
                    return new PtLocalSession(port, localTk, keyIndex);
                }
            } catch (Exception e) {
                AcademyCraft.LOGGER.debug("QQ music login stage pt_get_st failed on port={}", port, e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        AcademyCraft.LOGGER.warn("QQ music login stage pt_get_st failed on all localhost ports");
        return null;
    }

    private static String requestPtloginJumpCode(String normalizedUin, PtLocalSession session) {
        HttpURLConnection connection = null;
        try {
            var builder = PTLOGIN_JUMP_URL +
                    "?clientuin=" + normalizedUin +
                    "&keyindex=" + session.keyIndex() +
                    "&pt_aid=" + APPID +
                    "&daid=383" +
                    "&u1=" + URLEncoder.encode(LOGIN_JUMP_URL, StandardCharsets.UTF_8) +
                    "&pt_local_tk=" + session.localTk() +
                    "&pt_3rd_aid=" + THIRD_APPID +
                    "&ptopt=1&style=40";
            connection = (HttpURLConnection) new URL(builder).openConnection();
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Referer", buildXloginUrl());
            connection.setRequestProperty("Accept", "*/*");
            var cookieHeader = buildCookieHeader(AUTH_SESSION_COOKIES);
            if (!cookieHeader.isBlank()) {
                connection.setRequestProperty("Cookie", cookieHeader);
            }
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            var responseCode = connection.getResponseCode();
            var location = connection.getHeaderField("Location");
            var refresh = connection.getHeaderField("Refresh");
            var body = responseCode >= 400 || location == null ? safeReadResponse(connection) : "";
            captureCookies(connection, AUTH_SESSION_COOKIES);
            connection.disconnect();
            var authCode = extractCode(location, refresh);
            if (authCode == null || authCode.isBlank()) {
                authCode = extractCallbackCode(body);
            }
            AcademyCraft.LOGGER.info("QQ music login stage ptlogin_jump code={}, nextLocation={}, hasRefresh={}, cookies={}, body={}",
                    responseCode, location, refresh != null, AUTH_SESSION_COOKIES.keySet(), summarize(body));
            if (authCode != null && !authCode.isBlank()) {
                return authCode;
            }
            var nextUrl = location;
            if (nextUrl == null || nextUrl.isBlank()) {
                nextUrl = extractNextUrl(body, refresh);
            }
            if (nextUrl == null || nextUrl.isBlank()) {
                return null;
            }
            return followAuthorizeCodeRedirects(nextUrl);
        } catch (Exception e) {
            AcademyCraft.LOGGER.error("QQ music login stage ptlogin_jump failed", e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String requestXloginCode() {
        HttpURLConnection connection = null;
        try {
            var xloginUrl = buildXloginUrl();
            connection = (HttpURLConnection) new URL(xloginUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Referer", "https://graph.qq.com/");
            var cookieHeader = buildCookieHeader(AUTH_SESSION_COOKIES);
            if (!cookieHeader.isBlank()) {
                connection.setRequestProperty("Cookie", cookieHeader);
            }
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            var responseCode = connection.getResponseCode();
            var location = connection.getHeaderField("Location");
            var refresh = connection.getHeaderField("Refresh");
            var body = responseCode >= 400 || location == null ? safeReadResponse(connection) : "";
            captureCookies(connection, AUTH_SESSION_COOKIES);
            connection.disconnect();
            var authCode = extractCode(location, refresh);
            if (authCode == null || authCode.isBlank()) {
                authCode = extractCallbackCode(body);
            }
            AcademyCraft.LOGGER.info("QQ music login stage xlogin code={}, nextLocation={}, hasRefresh={}, cookies={}, body={}",
                    responseCode, location, refresh != null, AUTH_SESSION_COOKIES.keySet(), summarize(body));
            if (authCode != null && !authCode.isBlank()) {
                return authCode;
            }
            var nextUrl = location;
            if (nextUrl == null || nextUrl.isBlank()) {
                nextUrl = extractNextUrl(body, refresh);
            }
            if (nextUrl == null || nextUrl.isBlank()) {
                return null;
            }
            return followAuthorizeCodeRedirects(nextUrl);
        } catch (Exception e) {
            AcademyCraft.LOGGER.error("QQ music login stage xlogin failed", e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String buildXloginUrl() {
        var builder = XLOGIN_URL + "?appid=" + APPID +
                "&daid=383" +
                "&style=33" +
                "&login_text=" + URLEncoder.encode("登录", StandardCharsets.UTF_8) +
                "&hide_title_bar=1" +
                "&hide_border=1" +
                "&target=self" +
                "&s_url=" + URLEncoder.encode(LOGIN_JUMP_URL, StandardCharsets.UTF_8) +
                "&pt_3rd_aid=" + THIRD_APPID +
                "&pt_feedback_link=" +
                URLEncoder.encode("https://support.qq.com/products/77942?customInfo=.appid" + THIRD_APPID, StandardCharsets.UTF_8) +
                "&theme=2&verify_theme=";
        return builder;
    }

    private static void updateStatus(String text) {
        lastStatusText = text == null || text.isBlank() ? "QQ 音乐：未登录" : text;
    }

    private static synchronized void clearAuthSessionCookies() {
        AUTH_SESSION_COOKIES.clear();
    }

    private static String safeReadResponse(HttpURLConnection connection) {
        try {
            return readResponse(connection);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String summarize(String text) {
        if (text == null) {
            return "";
        }
        var normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= 240) {
            return normalized;
        }
        return normalized.substring(0, 240) + "...";
    }

    private static String extractCookieValue(String cookie, String key) {
        var prefix = key + "=";
        for (var part : cookie.split(";")) {
            var trimmed = part.trim();
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length());
            }
        }
        return null;
    }

    private static String extractCode(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (var candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            var matcher = CODE_PATTERN.matcher(candidate);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private static String extractPortalCode(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (var candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            var matcher = Pattern.compile("code=([A-Z0-9]+)").matcher(candidate);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private static String extractNumericField(String body, String fieldName) {
        if (body == null || body.isBlank() || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        var quotedPattern = Pattern.compile(Pattern.quote(fieldName) + "[\"']?\\s*[:=]\\s*[\"']?(\\d+)");
        var quotedMatcher = quotedPattern.matcher(body);
        if (quotedMatcher.find()) {
            return quotedMatcher.group(1);
        }
        var callbackPattern = Pattern.compile(Pattern.quote(fieldName) + ".*?(\\d+)");
        var callbackMatcher = callbackPattern.matcher(body);
        if (callbackMatcher.find()) {
            return callbackMatcher.group(1);
        }
        var numericMatcher = NUMERIC_PATTERN.matcher(body);
        return numericMatcher.find() ? numericMatcher.group(1) : null;
    }

    private static String extractCallbackCode(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        var callbackCodeMatcher = CALLBACK_CODE_PATTERN.matcher(body);
        if (callbackCodeMatcher.find()) {
            return callbackCodeMatcher.group(1);
        }
        var callbackUrlMatcher = CALLBACK_URL_PATTERN.matcher(body);
        if (callbackUrlMatcher.find()) {
            return extractCode(callbackUrlMatcher.group());
        }
        return null;
    }

    private static String normalizeUin(String rawUin) {
        if (rawUin == null || rawUin.isBlank()) {
            return rawUin;
        }
        var matcher = NUMERIC_PATTERN.matcher(rawUin);
        return matcher.find() ? matcher.group(1) : rawUin;
    }

    private static void appendFormField(StringBuilder builder, String key, String value) {
        if (builder.length() > 0) {
            builder.append('&');
        }
        builder.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
        builder.append('=');
        builder.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String extractNextUrl(String body, String refresh) {
        if (refresh != null && !refresh.isBlank()) {
            var refreshMatcher = META_REFRESH_PATTERN.matcher(refresh);
            if (refreshMatcher.find()) {
                return refreshMatcher.group(1);
            }
        }
        if (body == null || body.isBlank()) {
            return null;
        }
        var ptuiMatcher = PTUI_REDIRECT_PATTERN.matcher(body);
        if (ptuiMatcher.find()) {
            return ptuiMatcher.group(1);
        }
        var jsMatcher = JS_URL_PATTERN.matcher(body);
        if (jsMatcher.find()) {
            return jsMatcher.group(1);
        }
        var urlMatcher = URL_PATTERN.matcher(body);
        while (urlMatcher.find()) {
            var url = urlMatcher.group();
            if (url.contains("code=") || url.contains("oauth2.0/show") || url.contains("common_login.html")) {
                return url;
            }
        }
        return null;
    }

    private static synchronized void captureCookies(HttpURLConnection connection, Map<String, String> target) {
        if (connection == null || target == null) {
            return;
        }
        for (var entry : connection.getHeaderFields().entrySet()) {
            if (!"Set-Cookie".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            for (var cookieStr : entry.getValue()) {
                var separator = cookieStr.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                var key = cookieStr.substring(0, separator).trim();
                var value = extractCookieValue(cookieStr, key);
                if (value != null && !value.isBlank()) {
                    target.put(key, value);
                }
            }
        }
    }

    private static String buildCookieHeader(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return "";
        }
        var builder = new StringBuilder();
        for (var entry : cookies.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static String buildCookieHeader(String... parts) {
        var builder = new StringBuilder();
        if (parts == null) {
            return "";
        }
        for (var part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(part);
        }
        return builder.toString();
    }

    private static long calculatePtqrtoken(String qrsig) {
        long value = 0;
        for (var i = 0; i < qrsig.length(); i++) {
            value += (value << 5) + qrsig.charAt(i);
            value &= 0x7FFFFFFF;
        }
        return value;
    }

    private static long calculateGtk(String skey) {
        long hash = 5381;
        for (var i = 0; i < skey.length(); i++) {
            hash += (hash << 5) + skey.charAt(i);
        }
        return hash & 0x7fffffff;
    }

    public enum LoginState {
        IDLE,
        FETCHING_QR,
        WAITING_SCAN,
        SUCCESS,
        FAILED,
        QR_EXPIRED
    }

    enum CredentialSource {
        JSON,
        COOKIE,
        NONE
    }

    public record QrCodeSession(byte[] imageBytes, String qrsig) {
    }

    record LoginExchangeResult(
            int rootCode,
            int serviceCode,
            String responseKey,
            CredentialSource credentialSource,
            QqCredential credential
    ) {
    }

    private record PtLocalSession(int port, String localTk, String keyIndex) {
    }
}
