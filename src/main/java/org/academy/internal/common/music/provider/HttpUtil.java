package org.academy.internal.common.music.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 提供者服务的最小 HTTP 工具，客户端与服务端共用喵。
 */
public final class HttpUtil {
    private HttpUtil() {
    }

    public static byte[] downloadBytes(
            String url,
            String userAgent,
            String referer,
            int connectTimeoutMillis,
            int readTimeoutMillis
    ) throws IOException {
        var connection = open(url, userAgent, referer);
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        var responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            connection.disconnect();
            throw new IOException("HTTP " + responseCode + " for " + maskUrl(url));
        }
        try (var stream = connection.getInputStream()) {
            return stream.readAllBytes();
        } finally {
            connection.disconnect();
        }
    }

    public static JsonObjectResponse getJson(
            String url,
            Map<String, String> headers
    ) throws IOException {
        var connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        applyHeaders(connection, headers);
        try {
            var body = readAll(connection);
            return new JsonObjectResponse(
                    JsonParser.parseString(body).getAsJsonObject(),
                    connection.getHeaderFields()
            );
        } finally {
            connection.disconnect();
        }
    }

    public static JsonObjectResponse postJson(
            String url,
            String body,
            Map<String, String> headers
    ) throws IOException {
        var connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        applyHeaders(connection, headers);
        try (var outputStream = connection.getOutputStream()) {
            outputStream.write(body.getBytes(StandardCharsets.UTF_8));
        }
        try {
            var responseBody = readAll(connection);
            return new JsonObjectResponse(
                    JsonParser.parseString(responseBody).getAsJsonObject(),
                    connection.getHeaderFields()
            );
        } finally {
            connection.disconnect();
        }
    }

    public static String formBody(Map<String, Object> params) {
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

    private static HttpURLConnection open(String url, String userAgent, String referer) throws IOException {
        var connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestProperty("User-Agent", userAgent);
        connection.setRequestProperty("Accept-Encoding", "identity");
        if (referer != null && !referer.isBlank()) {
            connection.setRequestProperty("Referer", referer);
        }
        return connection;
    }

    private static void applyHeaders(HttpURLConnection connection, Map<String, String> headers) {
        for (var entry : headers.entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
    }

    private static String readAll(HttpURLConnection connection) throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            var result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        }
    }

    private static String maskUrl(String url) {
        var queryIndex = url.indexOf('?');
        return queryIndex > 0 ? url.substring(0, queryIndex) : url;
    }

    public record JsonObjectResponse(JsonObject json, Map<String, List<String>> headerFields) {
    }
}
