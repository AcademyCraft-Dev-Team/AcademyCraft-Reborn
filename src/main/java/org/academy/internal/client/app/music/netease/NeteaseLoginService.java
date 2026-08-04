package org.academy.internal.client.app.music.netease;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class NeteaseLoginService {
    private static final String BASE_URL = "https://music.163.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.164 NeteaseMusicDesktop/3.1.6";

    public enum LoginState {
        IDLE,
        FETCHING_QR,
        WAITING_SCAN,
        SUCCESS,
        FAILED,
        QR_EXPIRED
    }

    public record QrCodeSession(byte[] imageBytes, String uniKey) {
    }

    private static String lastStatusText = "";
    private static String sessionCookie = "";

    private NeteaseLoginService() {
    }

    public static String getLastStatusText() {
        return lastStatusText;
    }

    private static Map<String, String> defaultHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Referer", "https://music.163.com");
        headers.put("Accept", "application/json, text/plain, */*");
        if (!sessionCookie.isBlank()) {
            headers.put("Cookie", sessionCookie);
        }
        return headers;
    }

    private static void updateCookie(Map<String, List<String>> headerFields) {
        List<String> setCookies = headerFields.get("Set-Cookie");
        if (setCookies != null) {
            for (String cookie : setCookies) {
                String value = cookie.split(";")[0];
                if (sessionCookie.isBlank()) {
                    sessionCookie = value;
                } else if (!sessionCookie.contains(value.split("=")[0] + "=")) {
                    sessionCookie += "; " + value;
                }
            }
            NeteaseCredentialManager.setCookieHeader(sessionCookie);
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
            updateCookie(connection.getHeaderFields());
            return JsonParser.parseString(result.toString()).getAsJsonObject();
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] getBytes(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
        try (InputStream stream = connection.getInputStream()) {
            byte[] bytes = stream.readAllBytes();
            updateCookie(connection.getHeaderFields());
            return bytes;
        } finally {
            connection.disconnect();
        }
    }

    public static CompletableFuture<QrCodeSession> fetchQrCode() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> headers = defaultHeaders();
                JsonObject keyResponse = getJson(BASE_URL + "/api/login/qrcode/unikey?type=1&timestamp=" + System.currentTimeMillis(), headers);
                if (keyResponse == null || !keyResponse.has("unikey")) {
                    throw new IOException("Failed to get QR code key, response: " + (keyResponse != null ? keyResponse : "null"));
                }
                String uniKey = keyResponse.get("unikey").getAsString();

                String qrUrl = BASE_URL + "/login?codekey=" + uniKey;
                byte[] imageBytes;
                try {
                    String qrApiUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" + java.net.URLEncoder.encode(qrUrl, StandardCharsets.UTF_8);
                    imageBytes = getBytes(qrApiUrl, headers);
                } catch (Exception e) {
                    try {
                        String qrApiUrl2 = "https://chart.googleapis.com/chart?chs=300x300&cht=qr&chl=" + java.net.URLEncoder.encode(qrUrl, StandardCharsets.UTF_8);
                        imageBytes = getBytes(qrApiUrl2, headers);
                    } catch (Exception e2) {
                        throw new IOException("All QR code generation APIs failed: " + e.getMessage(), e2);
                    }
                }

                return new QrCodeSession(imageBytes, uniKey);
            } catch (Exception e) {
                AcademyCraft.LOGGER.error("Failed to fetch NetEase QR code", e);
                throw new RuntimeException(e);
            }
        }, AcademyCraft.executorService);
    }

    public static CompletableFuture<LoginState> pollLogin(String uniKey) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> headers = defaultHeaders();
                JsonObject response = getJson(BASE_URL + "/api/login/qrcode/client/login?type=1&key=" + uniKey + "&timestamp=" + System.currentTimeMillis(), headers);

                if (response == null) {
                    return LoginState.FAILED;
                }

                int code = response.has("code") ? response.get("code").getAsInt() : -1;
                switch (code) {
                    case 800:
                        lastStatusText = "二维码已过期";
                        return LoginState.QR_EXPIRED;
                    case 801:
                        lastStatusText = "请使用手机网易云音乐扫码";
                        return LoginState.WAITING_SCAN;
                    case 802:
                        lastStatusText = "请在手机上确认登录";
                        return LoginState.WAITING_SCAN;
                    case 803:
                        lastStatusText = "登录成功";
                        updateLoginStatus();
                        return LoginState.SUCCESS;
                    default:
                        String message = response.has("message") ? response.get("message").getAsString() : "未知状态";
                        lastStatusText = "登录状态: " + message;
                        return LoginState.WAITING_SCAN;
                }
            } catch (Exception e) {
                AcademyCraft.LOGGER.error("Failed to poll NetEase login", e);
                return LoginState.FAILED;
            }
        }, AcademyCraft.executorService);
    }

    private static void updateLoginStatus() {
        try {
            Map<String, String> headers = defaultHeaders();
            JsonObject response = getJson(BASE_URL + "/api/w/nuser/account/get?timestamp=" + System.currentTimeMillis(), headers);

            if (response != null && response.has("account") && !response.get("account").isJsonNull()) {
                JsonObject profile = response.getAsJsonObject("profile");
                String uid = String.valueOf(profile.get("userId").getAsLong());
                String nickname = profile.has("nickname") ? profile.get("nickname").getAsString() : "";
                boolean defaultAvatar = profile.has("defaultAvatar") && profile.get("defaultAvatar").getAsBoolean();
                String avatarUrl = defaultAvatar ? "" : (profile.has("avatarUrl") ? profile.get("avatarUrl").getAsString() : "");

                NeteaseCredential credential = new NeteaseCredential(uid, nickname, avatarUrl);
                NeteaseCredentialManager.save(credential);
            }
        } catch (Exception e) {
            AcademyCraft.LOGGER.error("Failed to update NetEase login status", e);
        }
    }

    public static void logout() {
        try {
            Map<String, String> headers = defaultHeaders();
            getJson(BASE_URL + "/api/logout", headers);
        } catch (Exception ignored) {
        }
        sessionCookie = "";
        NeteaseCredentialManager.setCookieHeader("");
        NeteaseCredentialManager.clear();
    }
}
