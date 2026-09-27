package org.academy.internal.client.app.music.netease

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.academy.AcademyCraft
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

object NeteaseLoginService {
    private const val BASE_URL = "https://music.163.com"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/91.0.4472.164 NeteaseMusicDesktop/3.1.6"

    private var sessionCookie = ""

    private fun defaultHeaders(): MutableMap<String, String> {
        val headers = HashMap<String, String>()
        headers["User-Agent"] = USER_AGENT
        headers["Referer"] = "https://music.163.com"
        headers["Accept"] = "application/json, text/plain, */*"
        if (sessionCookie.isNotBlank()) {
            headers["Cookie"] = sessionCookie
        }
        return headers
    }

    private fun updateCookie(headerFields: Map<String, List<String>>) {
        val setCookies = headerFields["Set-Cookie"]
        if (setCookies != null) {
            for (cookie in setCookies) {
                val value = cookie.split(";")[0]
                if (sessionCookie.isBlank()) {
                    sessionCookie = value
                } else if (!sessionCookie.contains(value.split("=")[0] + "=")) {
                    sessionCookie = "$sessionCookie; $value"
                }
            }
            NeteaseCredentialManager.setCookieHeader(sessionCookie)
        }
    }

    @Throws(IOException::class)
    private fun getJson(url: String, headers: Map<String, String>): JsonObject? {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 12000
        connection.readTimeout = 12000
        for ((key, value) in headers) {
            connection.setRequestProperty(key, value)
        }
        try {
            BufferedReader(
                InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)
            ).use { reader ->
                val result = StringBuilder()
                var line = reader.readLine()
                while (line != null) {
                    result.append(line)
                    line = reader.readLine()
                }
                updateCookie(connection.headerFields)
                return JsonParser.parseString(result.toString()).asJsonObject
            }
        } finally {
            connection.disconnect()
        }
    }

    @Throws(IOException::class)
    private fun getBytes(url: String, headers: Map<String, String>): ByteArray {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 12000
        connection.readTimeout = 12000
        for ((key, value) in headers) {
            connection.setRequestProperty(key, value)
        }
        try {
            connection.inputStream.use { stream ->
                val bytes = stream.readAllBytes()
                updateCookie(connection.headerFields)
                return bytes
            }
        } finally {
            connection.disconnect()
        }
    }

    fun fetchQrCode(): CompletableFuture<QrCodeSession> {
        return CompletableFuture.supplyAsync({
            try {
                val headers = defaultHeaders()
                val keyResponse = getJson(
                    BASE_URL + "/api/login/qrcode/unikey?type=1&timestamp=" + System.currentTimeMillis(),
                    headers,
                )
                if (keyResponse == null || !keyResponse.has("unikey")) {
                    throw IOException("Failed to get QR code key, response: " + (keyResponse ?: "null"))
                }
                val uniKey = keyResponse.get("unikey").asString

                val qrUrl = "$BASE_URL/login?codekey=$uniKey"
                val imageBytes: ByteArray = try {
                    val qrApiUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" +
                            URLEncoder.encode(qrUrl, StandardCharsets.UTF_8)
                    getBytes(qrApiUrl, headers)
                } catch (e: Exception) {
                    try {
                        val qrApiUrl2 = "https://chart.googleapis.com/chart?chs=300x300&cht=qr&chl=" +
                                URLEncoder.encode(qrUrl, StandardCharsets.UTF_8)
                        getBytes(qrApiUrl2, headers)
                    } catch (e2: Exception) {
                        throw IOException("All QR code generation APIs failed: " + e.message, e2)
                    }
                }

                QrCodeSession(imageBytes, uniKey)
            } catch (e: Exception) {
                AcademyCraft.LOGGER.error("Failed to fetch NetEase QR code", e)
                throw RuntimeException(e)
            }
        }, AcademyCraft.executorService)
    }

    fun pollLogin(uniKey: String): CompletableFuture<LoginState> {
        return CompletableFuture.supplyAsync({
            try {
                val headers = defaultHeaders()
                val response = getJson(
                    BASE_URL + "/api/login/qrcode/client/login?type=1&key=" + uniKey +
                            "&timestamp=" + System.currentTimeMillis(),
                    headers,
                )

                if (response == null) {
                    return@supplyAsync LoginState.FAILED
                }

                val code = if (response.has("code")) response.get("code").asInt else -1
                when (code) {
                    800 -> {
                        LoginState.QR_EXPIRED
                    }

                    801 -> {
                        LoginState.WAITING_SCAN
                    }

                    802 -> {
                        LoginState.WAITING_SCAN
                    }

                    803 -> {
                        updateLoginStatus()
                        LoginState.SUCCESS
                    }

                    else -> LoginState.WAITING_SCAN
                }
            } catch (e: Exception) {
                AcademyCraft.LOGGER.error("Failed to poll NetEase login", e)
                LoginState.FAILED
            }
        }, AcademyCraft.executorService)
    }

    private fun updateLoginStatus() {
        try {
            val headers = defaultHeaders()
            val response = getJson(
                BASE_URL + "/api/w/nuser/account/get?timestamp=" + System.currentTimeMillis(),
                headers,
            )

            if (response != null && response.has("account") && !response.get("account").isJsonNull) {
                val profile = response.getAsJsonObject("profile")
                val uid = profile.get("userId").asLong.toString()
                val nickname = if (profile.has("nickname")) profile.get("nickname").asString else ""
                val defaultAvatar = profile.has("defaultAvatar") && profile.get("defaultAvatar").asBoolean
                val avatarUrl =
                    if (defaultAvatar) "" else if (profile.has("avatarUrl")) profile.get("avatarUrl").asString else ""

                val credential = NeteaseCredential(uid, nickname, avatarUrl)
                NeteaseCredentialManager.save(credential)
            }
        } catch (e: Exception) {
            AcademyCraft.LOGGER.error("Failed to update NetEase login status", e)
        }
    }

    fun logout() {
        try {
            val headers = defaultHeaders()
            getJson("$BASE_URL/api/logout", headers)
        } catch (_: Exception) {
        }
        sessionCookie = ""
        NeteaseCredentialManager.setCookieHeader("")
        NeteaseCredentialManager.clear()
    }

    enum class LoginState {
        IDLE,
        WAITING_SCAN,
        SUCCESS,
        FAILED,
        QR_EXPIRED,
    }

    class QrCodeSession(val imageBytes: ByteArray, val uniKey: String)
}
