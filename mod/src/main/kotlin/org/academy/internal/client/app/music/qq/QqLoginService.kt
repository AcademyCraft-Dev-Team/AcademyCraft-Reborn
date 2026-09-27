package org.academy.internal.client.app.music.qq

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
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

object QqLoginService {
    private const val APPID = "716027609"
    private const val THIRD_APPID = "100497308"
    private const val REDIRECT_URI = "https://y.qq.com/wk_v17/common_login.html?type=QQ&&redirect="
    private const val QR_SHOW_URL = "https://xui.ptlogin2.qq.com/ssl/ptqrshow"
    private const val QR_LOGIN_URL = "https://xui.ptlogin2.qq.com/ssl/ptqrlogin"
    private const val AUTHORIZE_URL = "https://graph.qq.com/oauth2.0/authorize"
    private const val MUSICU_URL = "https://u6.y.qq.com/cgi-bin/musicu.fcg"
    private const val LOGIN_JUMP_URL = "https://graph.qq.com/oauth2.0/login_jump"
    private const val XLOGIN_URL = "https://xui.ptlogin2.qq.com/cgi-bin/xlogin"
    private const val CONCERTO_REDIRECT_URI =
        "https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/"
    private const val LOCAL_PTLOGIN_HOST = "https://localhost.ptlogin2.qq.com"
    private const val PTLOGIN_JUMP_URL = "https://ssl.ptlogin2.qq.com/jump"

    private val PTUI_CB = Regex("ptuiCB\\('(\\d+)','[^']*','([^']*)','[^']*','([^']*)'")
    private val PTUI_REDIRECT_PATTERN = Regex("ptui(?:_qlogin)?CB\\('[^']*','[^']*','([^']*)'")
    private val CODE_PATTERN = Regex("code=([^&]+)")
    private val PORTAL_CODE_PATTERN = Regex("code=([A-Z0-9]+)")
    private val CALLBACK_URL_PATTERN = Regex("https://y\\.qq\\.com/wk_v17/common_login\\.html[^\\s\"'<>]+")
    private val CALLBACK_CODE_PATTERN =
        Regex("https://y\\.qq\\.com/wk_v17/common_login\\.html[^\\s\"'<>]*[?&]code=([A-Za-z0-9]+)")
    private val URL_PATTERN = Regex("https?://[^\\s\"'<>]+")
    private val JS_URL_PATTERN = Regex(
        "(?:location\\.(?:href|replace|assign)|top\\.location|window\\.location)\\s*[=(]\\s*[\"']([^\"']+)[\"']"
    )
    private val META_REFRESH_PATTERN = Regex("url=([^\"'>\\s]+)", RegexOption.IGNORE_CASE)
    private val NUMERIC_PATTERN = Regex("(\\d+)")

    private val AUTH_SESSION_COOKIES = LinkedHashMap<String, String>()

    private val EXECUTOR: ExecutorService = Executors.newSingleThreadExecutor(object : ThreadFactory {
        private val counter = AtomicInteger(1)

        override fun newThread(runnable: Runnable): Thread {
            val thread = Thread(runnable, "academy-qq-login-${counter.getAndIncrement()}")
            thread.isDaemon = true
            return thread
        }
    })

    fun fetchQrCode(): CompletableFuture<QrCodeSession> {
        return CompletableFuture.supplyAsync({
            try {
                clearAuthSessionCookies()
                val urlBuilder = QR_SHOW_URL +
                        "?appid=" + APPID +
                        "&e=2&l=M&s=3&d=72&v=4&t=0.787&daid=383" +
                        "&pt_3rd_aid=" + THIRD_APPID +
                        "&u1=" + URLEncoder.encode(LOGIN_JUMP_URL, StandardCharsets.UTF_8)

                val connection = URI.create(urlBuilder).toURL().openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                connection.connect()

                var qrsig: String? = null
                for ((key, values) in connection.headerFields) {
                    if (!"Set-Cookie".equals(key, ignoreCase = true)) {
                        continue
                    }
                    for (cookieStr in values) {
                        val value = extractCookieValue(cookieStr, "qrsig")
                        if (value != null) {
                            qrsig = value
                            break
                        }
                    }
                    if (qrsig != null) {
                        break
                    }
                }

                val imageData = connection.inputStream.readAllBytes()
                connection.disconnect()
                if (qrsig.isNullOrBlank()) {
                    AcademyCraft.LOGGER.error("QQ music login stage fetchQrCode failed: missing qrsig")
                    throw IOException("Failed to obtain qrsig")
                }
                AcademyCraft.LOGGER.info("QQ music login stage fetchQrCode ok, hasQrsig={}", true)
                QrCodeSession(imageData, qrsig)
            } catch (e: Exception) {
                throw RuntimeException("Failed to fetch QQ music login QR code", e)
            }
        }, EXECUTOR)
    }

    fun pollLogin(qrsig: String?): CompletableFuture<LoginState> {
        return CompletableFuture.supplyAsync({
            try {
                if (qrsig.isNullOrBlank()) {
                    return@supplyAsync LoginState.FAILED
                }
                val ptqrtoken = calculatePtqrtoken(qrsig)
                val urlBuilder = QR_LOGIN_URL +
                        "?u1=" + URLEncoder.encode(LOGIN_JUMP_URL, StandardCharsets.UTF_8) +
                        "&ptqrtoken=" + ptqrtoken +
                        "&ptredirect=0&h=1&t=1&g=1&from_ui=1&ptlang=2052" +
                        "&js_ver=25072815&js_type=1&login_sig=&pt_uistyle=40" +
                        "&aid=" + APPID +
                        "&daid=383" +
                        "&pt_3rd_aid=" + THIRD_APPID

                val connection = URI.create(urlBuilder).toURL().openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Cookie", "qrsig=$qrsig")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                val body = readResponse(connection)
                connection.disconnect()
                AcademyCraft.LOGGER.debug(
                    "QQ music login stage ptqrlogin code={}, body={}", responseCode, summarize(body)
                )

                val match = PTUI_CB.find(body)
                if (match == null) {
                    AcademyCraft.LOGGER.error(
                        "QQ music login stage ptqrlogin failed: ptuiCB missing, body={}", summarize(body)
                    )
                    return@supplyAsync LoginState.WAITING_SCAN
                }
                val code = match.groupValues[1]
                if ("65" == code) {
                    return@supplyAsync LoginState.QR_EXPIRED
                }
                if ("0" != code) {
                    return@supplyAsync LoginState.WAITING_SCAN
                }
                var checkSigUrl = match.groupValues[2]
                if (checkSigUrl.isBlank()) {
                    checkSigUrl = match.groupValues[3]
                }
                AcademyCraft.LOGGER.info("QQ music login stage ptqrlogin ok, checkSigUrl={}", checkSigUrl)
                processLoginSuccess(checkSigUrl)
            } catch (e: Exception) {
                AcademyCraft.LOGGER.error("QQ music login poll failed", e)
                LoginState.FAILED
            }
        }, EXECUTOR)
    }

    private fun processLoginSuccess(checkSigUrl: String): LoginState {
        try {
            val connection = URI.create(checkSigUrl).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.setRequestProperty(
                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
            connection.setRequestProperty(
                "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.connect()
            val responseCode = connection.responseCode

            var uin: String? = null
            var ptOauthToken: String? = null
            var pSkey: String? = null
            for ((key, values) in connection.headerFields) {
                if (!"Set-Cookie".equals(key, ignoreCase = true)) {
                    continue
                }
                for (cookieStr in values) {
                    extractCookieValue(cookieStr, "pt2gguin")?.let { uin = it }
                    extractCookieValue(cookieStr, "pt_oauth_token")?.let { ptOauthToken = it }
                    extractCookieValue(cookieStr, "p_skey")?.let { pSkey = it }
                }
            }
            captureCookies(connection, AUTH_SESSION_COOKIES)
            val checkSigRedirect = connection.getHeaderField("Location")
            connection.disconnect()
            AcademyCraft.LOGGER.info(
                "QQ music login stage checkSig code={}, hasUin={}, hasOauthToken={}, hasPSkey={}, hasLocation={}, cookies={}",
                responseCode, uin != null, ptOauthToken != null, pSkey != null, checkSigRedirect != null,
                AUTH_SESSION_COOKIES.keys
            )

            if (uin == null || ptOauthToken == null || pSkey == null) {
                AcademyCraft.LOGGER.error("QQ music login stage checkSig failed: missing cookies")
                return LoginState.FAILED
            }
            followAuthorizeSessionRedirects(checkSigRedirect)

            var authCode = authorizeViaConcertoFlow()
            if (authCode.isNullOrBlank()) {
                authCode = requestXloginCode()
            }
            if (authCode.isNullOrBlank()) {
                authCode = requestLocalJumpCode(uin)
            }
            if (authCode.isNullOrBlank()) {
                authCode = requestLoginJumpCode()
            }
            if (authCode.isNullOrBlank()) {
                authCode = authorize(uin, ptOauthToken, pSkey)
            }
            if (authCode == null) {
                return LoginState.FAILED
            }
            val credential = qqConnectLoginServer(authCode) ?: return LoginState.FAILED
            QqCredentialManager.save(credential)
            AcademyCraft.LOGGER.info(
                "QQ music login success, musicId={}, expiresIn={}",
                credential.musicId, credential.keyExpiresIn
            )
            return LoginState.SUCCESS
        } catch (e: Exception) {
            AcademyCraft.LOGGER.error("QQ music login processing failed", e)
            return LoginState.FAILED
        }
    }

    @Throws(IOException::class)
    private fun authorize(uin: String, ptOauthToken: String, pSkey: String): String? {
        val gtk = calculateGtk(pSkey)
        val formData = LinkedHashMap<String, String>()
        formData["response_type"] = "code"
        formData["client_id"] = THIRD_APPID
        formData["redirect_uri"] = REDIRECT_URI
        formData["scope"] = "get_user_info"
        formData["state"] = "y_new.top.pop.logout"
        formData["switch"] = ""
        formData["from_ptlogin"] = "1"
        formData["src"] = "1"
        formData["update_auth"] = "1"
        formData["openapi"] = "1010"
        formData["g_tk"] = gtk.toString()
        formData["auth_time"] = (System.currentTimeMillis() / 1000).toString()

        val formBody = formData.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, StandardCharsets.UTF_8)}=${
                URLEncoder.encode(
                    it.value,
                    StandardCharsets.UTF_8
                )
            }"
        }

        val connection = URI.create(AUTHORIZE_URL).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.setRequestProperty(
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        )
        connection.setRequestProperty(
            "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        )
        connection.setRequestProperty("Referer", LOGIN_JUMP_URL)
        connection.setRequestProperty(
            "Cookie", buildCookieHeader(
                "p_uin=$uin",
                "pt_oauth_token=$ptOauthToken",
                "p_skey=$pSkey",
                buildCookieHeader(AUTH_SESSION_COOKIES),
            )
        )
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.outputStream.use { it.write(formBody.toByteArray(StandardCharsets.UTF_8)) }
        val responseCode = connection.responseCode
        val location = connection.getHeaderField("Location")
        val body = if (location == null) safeReadResponse(connection) else ""
        val refresh = connection.getHeaderField("Refresh")
        captureCookies(connection, AUTH_SESSION_COOKIES)
        connection.disconnect()
        AcademyCraft.LOGGER.info(
            "QQ music login stage authorize code={}, hasLocation={}, hasRefresh={}, cookies={}, body={}",
            responseCode, location != null, refresh != null, AUTH_SESSION_COOKIES.keys, summarize(body)
        )
        if (location == null) {
            AcademyCraft.LOGGER.error(
                "QQ music login stage authorize failed: no redirect location, body={}", summarize(body)
            )
            return null
        }
        val match = CODE_PATTERN.find(location)
        if (match != null) {
            val authCode = match.groupValues[1]
            AcademyCraft.LOGGER.info("QQ music login stage authorize ok: codeLength={}", authCode.length)
            return authCode
        }
        val authCode = followAuthorizeCodeRedirects(location)
        if (authCode.isNullOrBlank()) {
            AcademyCraft.LOGGER.error(
                "QQ music login stage authorize failed: no code found after follow, location={}, refresh={}, body={}",
                location, refresh, summarize(body)
            )
            return null
        }
        AcademyCraft.LOGGER.info("QQ music login stage authorize ok after follow: codeLength={}", authCode.length)
        return authCode
    }

    private fun authorizeViaConcertoFlow(): String? {
        try {
            val pSkey = AUTH_SESSION_COOKIES["p_skey"]?.takeIf { it.isNotBlank() } ?: ""
            val gtk = calculateGtk(pSkey)
            val ui = AUTH_SESSION_COOKIES.getOrPut("ui") { UUID.randomUUID().toString().uppercase() }
            AUTH_SESSION_COOKIES["gtk"] = gtk.toString()

            val formBody = StringBuilder()
            appendFormField(formBody, "response_type", "code")
            appendFormField(formBody, "client_id", THIRD_APPID)
            appendFormField(formBody, "redirect_uri", CONCERTO_REDIRECT_URI)
            appendFormField(formBody, "scope", "get_user_info,get_app_friends")
            appendFormField(formBody, "state", "state")
            appendFormField(formBody, "switch", "")
            appendFormField(formBody, "from_ptlogin", "1")
            appendFormField(formBody, "src", "1")
            appendFormField(formBody, "update_auth", "1")
            appendFormField(formBody, "openapi", "80901010")
            appendFormField(formBody, "g_tk", gtk.toString())
            appendFormField(formBody, "auth_time", (System.currentTimeMillis() / 1000).toString())
            appendFormField(formBody, "ui", ui)

            val connection = URI.create(AUTHORIZE_URL).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty(
                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
            connection.setRequestProperty(
                "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            connection.setRequestProperty("Referer", "https://graph.qq.com")
            val cookieHeader = buildCookieHeader(AUTH_SESSION_COOKIES)
            if (cookieHeader.isNotBlank()) {
                connection.setRequestProperty("Cookie", cookieHeader)
            }
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.outputStream.use { it.write(formBody.toString().toByteArray(StandardCharsets.UTF_8)) }
            val responseCode = connection.responseCode
            val location = connection.getHeaderField("Location")
            val body = if (location == null) safeReadResponse(connection) else ""
            captureCookies(connection, AUTH_SESSION_COOKIES)
            connection.disconnect()

            val authCode = extractPortalCode(location, body)
            AcademyCraft.LOGGER.info(
                "QQ music login stage concerto-authorize code={}, nextLocation={}, cookies={}, body={}",
                responseCode, location, AUTH_SESSION_COOKIES.keys, summarize(body)
            )
            if (authCode.isNullOrBlank()) {
                return null
            }
            AcademyCraft.LOGGER.info("QQ music login stage concerto-authorize ok: codeLength={}", authCode.length)
            return authCode
        } catch (e: Exception) {
            AcademyCraft.LOGGER.error("QQ music login stage concerto-authorize failed", e)
            return null
        }
    }

    private fun qqConnectLoginServer(code: String): QqCredential? {
        var connection: HttpURLConnection? = null
        try {
            val comm = JsonObject()
            comm.addProperty("g_tk", AUTH_SESSION_COOKIES.getOrDefault("gtk", "5381").toLong())
            comm.addProperty("platform", "yqq")
            comm.addProperty("ct", 24)
            comm.addProperty("cv", 0)

            val param = JsonObject()
            param.addProperty("code", code)

            val req = JsonObject()
            req.addProperty("module", "QQConnectLogin.LoginServer")
            req.addProperty("method", "QQLogin")
            req.add("param", param)

            val body = JsonObject()
            body.add("comm", comm)
            body.add("req", req)

            connection = URI.create(MUSICU_URL).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.setRequestProperty("Content-Type", "application/json;charset=utf-8")
            connection.setRequestProperty(
                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
            connection.setRequestProperty("Accept", "application/json, text/plain, */*")
            connection.setRequestProperty("Origin", "https://y.qq.com")
            connection.setRequestProperty("Referer", "https://y.qq.com/")
            val cookieHeader = buildCookieHeader(AUTH_SESSION_COOKIES)
            if (cookieHeader.isNotBlank()) {
                connection.setRequestProperty("Cookie", cookieHeader)
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            val responseCode = connection.responseCode
            val responseBody = readResponse(connection)
            captureCookies(connection, AUTH_SESSION_COOKIES)
            val result = parseLoginExchangeResponse(responseBody, AUTH_SESSION_COOKIES)
            AcademyCraft.LOGGER.info(
                "QQ music login stage QQConnectLogin httpCode={}, rootCode={}, serviceCode={}, responseKey={}, credentialSource={}, cookies={}",
                responseCode, result.rootCode, result.serviceCode, result.responseKey,
                result.credentialSource, AUTH_SESSION_COOKIES.keys
            )
            if (result.credential == null) {
                AcademyCraft.LOGGER.error(
                    "QQ music login stage QQConnectLogin failed: rootCode={}, serviceCode={}, responseKey={}",
                    result.rootCode, result.serviceCode, result.responseKey
                )
            }
            return result.credential
        } catch (e: Exception) {
            AcademyCraft.LOGGER.error("QQ music login stage QQConnectLogin failed", e)
            return null
        } finally {
            connection?.disconnect()
        }
    }

    internal fun parseLoginExchangeResponse(responseBody: String?, cookies: Map<String, String>): LoginExchangeResult {
        var rootCode = Int.MIN_VALUE
        var serviceCode = Int.MIN_VALUE
        var responseKey = ""
        var data: JsonObject? = null
        try {
            val parsed = JsonParser.parseString(responseBody ?: "")
            if (parsed.isJsonObject) {
                val root = parsed.asJsonObject
                rootCode = getInt(root, "code", Int.MIN_VALUE)
                var serviceResponse = getObject(root, "req")
                if (serviceResponse != null) {
                    responseKey = "req"
                } else {
                    serviceResponse = getObject(root, "music.login.LoginServer.Login")
                    if (serviceResponse != null) {
                        responseKey = "music.login.LoginServer.Login"
                    }
                }
                if (serviceResponse != null) {
                    serviceCode = getInt(serviceResponse, "code", Int.MIN_VALUE)
                    if (serviceCode == 0) {
                        data = getObject(serviceResponse, "data")
                    }
                }
            }
        } catch (_: RuntimeException) {
            // A cookie-only response is still valid for older QQ Music login servers.
        }

        val jsonCredential = credentialFromData(data, cookies)
        if (jsonCredential != null) {
            return LoginExchangeResult(rootCode, serviceCode, responseKey, CredentialSource.JSON, jsonCredential)
        }
        val cookieCredential = credentialFromCookies(cookies)
        if (cookieCredential != null) {
            return LoginExchangeResult(rootCode, serviceCode, responseKey, CredentialSource.COOKIE, cookieCredential)
        }
        return LoginExchangeResult(rootCode, serviceCode, responseKey, CredentialSource.NONE, null)
    }

    private fun credentialFromData(data: JsonObject?, cookies: Map<String, String>?): QqCredential? {
        if (data == null) {
            return null
        }
        var musicId: String? = normalizeUin(
            firstNonBlank(
                getString(data, "str_musicid"),
                getString(data, "musicid"),
                cookies?.get("uin"),
                cookies?.get("wxuin"),
            )
        )
        if ("0" == musicId) {
            musicId = null
        }
        val musicKey = firstNonBlank(
            getString(data, "musickey"),
            getString(data, "musicKey"),
            cookies?.get("qm_keyst"),
            cookies?.get("qqmusic_key"),
        )
        val credential = QqCredential(
            musicId ?: "",
            musicKey ?: "",
            getLong(data, "keyExpiresIn", 0L),
            getLong(data, "musickeyCreateTime", System.currentTimeMillis() / 1000),
            getString(data, "refresh_key"),
            getString(data, "refresh_token"),
        )
        return if (credential.isValid()) credential else null
    }

    private fun credentialFromCookies(cookies: Map<String, String>?): QqCredential? {
        if (cookies.isNullOrEmpty()) {
            return null
        }
        val musicId = normalizeUin(firstNonBlank(cookies["uin"], cookies["wxuin"]))
        val musicKey = firstNonBlank(cookies["qm_keyst"], cookies["qqmusic_key"])
        val credential = QqCredential(
            musicId ?: "", musicKey ?: "", 0L, System.currentTimeMillis() / 1000, "", ""
        )
        return if (credential.isValid()) credential else null
    }

    private fun getObject(obj: JsonObject?, key: String): JsonObject? {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonObject) {
            return null
        }
        return obj.getAsJsonObject(key)
    }

    private fun getString(obj: JsonObject?, key: String): String {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull) {
            return ""
        }
        return try {
            obj.get(key).asString
        } catch (_: RuntimeException) {
            ""
        }
    }

    private fun getInt(obj: JsonObject?, key: String, fallback: Int): Int {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull) {
            return fallback
        }
        return try {
            obj.get(key).asInt
        } catch (_: RuntimeException) {
            fallback
        }
    }

    private fun getLong(obj: JsonObject?, key: String, fallback: Long): Long {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull) {
            return fallback
        }
        return try {
            obj.get(key).asLong
        } catch (_: RuntimeException) {
            fallback
        }
    }

    @Throws(IOException::class)
    private fun readResponse(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode >= 400) connection.errorStream else connection.inputStream
        if (stream == null) {
            throw IOException("Empty HTTP response body")
        }
        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { bufferedReader ->
            val builder = StringBuilder()
            var line = bufferedReader.readLine()
            while (line != null) {
                builder.append(line)
                line = bufferedReader.readLine()
            }
            return builder.toString()
        }
    }

    private fun followAuthorizeSessionRedirects(redirectUrl: String?) {
        if (redirectUrl.isNullOrBlank()) {
            AcademyCraft.LOGGER.warn("QQ music login stage session follow skipped: empty redirect url")
            return
        }
        var currentUrl: String? = redirectUrl
        var referer: String? = LOGIN_JUMP_URL
        var step = 0
        while (step < 5 && !currentUrl.isNullOrBlank()) {
            val url = currentUrl
            var connection: HttpURLConnection? = null
            try {
                connection = URI.create(url).toURL().openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = false
                connection.setRequestProperty(
                    "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
                connection.setRequestProperty(
                    "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                )
                if (!referer.isNullOrBlank()) {
                    connection.setRequestProperty("Referer", referer)
                }
                val cookieHeader = buildCookieHeader(AUTH_SESSION_COOKIES)
                if (cookieHeader.isNotBlank()) {
                    connection.setRequestProperty("Cookie", cookieHeader)
                }
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                val responseCode = connection.responseCode
                captureCookies(connection, AUTH_SESSION_COOKIES)
                val location = connection.getHeaderField("Location")
                val body = if (responseCode >= 400 || location == null) safeReadResponse(connection) else ""
                AcademyCraft.LOGGER.info(
                    "QQ music login stage session follow step={}, code={}, nextLocation={}, cookies={}, body={}",
                    step + 1, responseCode, location, AUTH_SESSION_COOKIES.keys, summarize(body)
                )
                if (location == null || responseCode < 300 || responseCode >= 400) {
                    return
                }
                referer = currentUrl
                currentUrl = location
            } catch (e: Exception) {
                AcademyCraft.LOGGER.error("QQ music login stage session follow failed at url={}", currentUrl, e)
                return
            } finally {
                connection?.disconnect()
            }
            step++
        }
    }

    private fun followAuthorizeCodeRedirects(startUrl: String?): String? {
        var currentUrl: String? = startUrl
        var referer: String? = LOGIN_JUMP_URL
        var step = 0
        while (step < 5 && !currentUrl.isNullOrBlank()) {
            val url = currentUrl
            var connection: HttpURLConnection? = null
            try {
                connection = URI.create(url).toURL().openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = false
                connection.setRequestProperty(
                    "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
                connection.setRequestProperty(
                    "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                )
                if (!referer.isNullOrBlank()) {
                    connection.setRequestProperty("Referer", referer)
                }
                val cookieHeader = buildCookieHeader(AUTH_SESSION_COOKIES)
                if (cookieHeader.isNotBlank()) {
                    connection.setRequestProperty("Cookie", cookieHeader)
                }
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                val responseCode = connection.responseCode
                val location = connection.getHeaderField("Location")
                val refresh = connection.getHeaderField("Refresh")
                val body = if (responseCode >= 400 || location == null) safeReadResponse(connection) else ""
                captureCookies(connection, AUTH_SESSION_COOKIES)
                connection.disconnect()

                var foundCode = extractCode(location, refresh)
                if (foundCode.isNullOrBlank()) {
                    foundCode = extractCallbackCode(body)
                }
                AcademyCraft.LOGGER.info(
                    "QQ music login stage authorize-follow step={}, code={}, nextLocation={}, hasRefresh={}, cookies={}, body={}",
                    step + 1, responseCode, location, refresh != null, AUTH_SESSION_COOKIES.keys, summarize(body)
                )
                if (!foundCode.isNullOrBlank()) {
                    return foundCode
                }

                var nextUrl = location
                if (nextUrl.isNullOrBlank()) {
                    nextUrl = extractNextUrl(body, refresh)
                }
                if (nextUrl.isNullOrBlank()) {
                    return null
                }
                referer = currentUrl
                currentUrl = nextUrl
            } catch (e: Exception) {
                AcademyCraft.LOGGER.error("QQ music login stage authorize-follow failed at url={}", currentUrl, e)
                return null
            } finally {
                connection?.disconnect()
            }
            step++
        }
        return null
    }

    private fun requestLoginJumpCode(): String? {
        var connection: HttpURLConnection? = null
        try {
            connection = URI.create(LOGIN_JUMP_URL).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.setRequestProperty(
                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
            connection.setRequestProperty(
                "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            connection.setRequestProperty("Referer", "https://graph.qq.com/")
            val cookieHeader = buildCookieHeader(AUTH_SESSION_COOKIES)
            if (cookieHeader.isNotBlank()) {
                connection.setRequestProperty("Cookie", cookieHeader)
            }
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            val responseCode = connection.responseCode
            val location = connection.getHeaderField("Location")
            val refresh = connection.getHeaderField("Refresh")
            val body = if (responseCode >= 400 || location == null) safeReadResponse(connection) else ""
            captureCookies(connection, AUTH_SESSION_COOKIES)
            connection.disconnect()
            var authCode = extractCode(location, refresh)
            if (authCode.isNullOrBlank()) {
                authCode = extractCallbackCode(body)
            }
            AcademyCraft.LOGGER.info(
                "QQ music login stage login_jump code={}, nextLocation={}, hasRefresh={}, cookies={}, body={}",
                responseCode, location, refresh != null, AUTH_SESSION_COOKIES.keys, summarize(body)
            )
            if (!authCode.isNullOrBlank()) {
                return authCode
            }
            var nextUrl = location
            if (nextUrl.isNullOrBlank()) {
                nextUrl = extractNextUrl(body, refresh)
            }
            if (nextUrl.isNullOrBlank()) {
                return null
            }
            return followAuthorizeCodeRedirects(nextUrl)
        } catch (e: Exception) {
            AcademyCraft.LOGGER.error("QQ music login stage login_jump failed", e)
            return null
        } finally {
            connection?.disconnect()
        }
    }

    private fun requestLocalJumpCode(uin: String?): String? {
        val normalizedUin = normalizeUin(uin)
        val ptLocalToken = AUTH_SESSION_COOKIES["pt_local_token"]
        if (normalizedUin.isNullOrBlank() || ptLocalToken.isNullOrBlank()) {
            AcademyCraft.LOGGER.info(
                "QQ music login stage local_jump skipped: hasUin={}, hasPtLocalToken={}",
                !normalizedUin.isNullOrBlank(), !ptLocalToken.isNullOrBlank()
            )
            return null
        }
        val session = fetchPtLocalSession(normalizedUin, ptLocalToken) ?: return null
        return requestPtloginJumpCode(normalizedUin, session)
    }

    private fun fetchPtLocalSession(normalizedUin: String, ptLocalToken: String): PtLocalSession? {
        for (port in 4301..4309 step 2) {
            var connection: HttpURLConnection? = null
            try {
                val builder = LOCAL_PTLOGIN_HOST +
                        ":" + port +
                        "/pt_get_st?clientuin=" + normalizedUin +
                        "&r=" + Math.random() +
                        "&pt_local_tk=" + URLEncoder.encode(ptLocalToken, StandardCharsets.UTF_8) +
                        "&pt_aid=" + APPID +
                        "&daid=383" +
                        "&pt_3rd_aid=" + THIRD_APPID +
                        "&u1=" + URLEncoder.encode(LOGIN_JUMP_URL, StandardCharsets.UTF_8) +
                        "&callback=__jp0"
                connection = URI.create(builder).toURL().openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = false
                connection.setRequestProperty(
                    "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
                connection.setRequestProperty("Referer", buildXloginUrl())
                connection.setRequestProperty("Accept", "*/*")
                connection.connectTimeout = 2000
                connection.readTimeout = 2000
                val body = readResponse(connection)
                val localTk = extractNumericField(body, "pt_local_tk")
                val keyIndex = extractNumericField(body, "keyindex")
                AcademyCraft.LOGGER.info(
                    "QQ music login stage pt_get_st port={}, hasLocalTk={}, keyIndex={}, body={}",
                    port, localTk != null, keyIndex, summarize(body)
                )
                if (localTk != null && keyIndex != null) {
                    return PtLocalSession(port, localTk, keyIndex)
                }
            } catch (e: Exception) {
                AcademyCraft.LOGGER.debug("QQ music login stage pt_get_st failed on port={}", port, e)
            } finally {
                connection?.disconnect()
            }
        }
        AcademyCraft.LOGGER.warn("QQ music login stage pt_get_st failed on all localhost ports")
        return null
    }

    private fun requestPtloginJumpCode(normalizedUin: String, session: PtLocalSession): String? {
        var connection: HttpURLConnection? = null
        try {
            val builder = PTLOGIN_JUMP_URL +
                    "?clientuin=" + normalizedUin +
                    "&keyindex=" + session.keyIndex +
                    "&pt_aid=" + APPID +
                    "&daid=383" +
                    "&u1=" + URLEncoder.encode(LOGIN_JUMP_URL, StandardCharsets.UTF_8) +
                    "&pt_local_tk=" + session.localTk +
                    "&pt_3rd_aid=" + THIRD_APPID +
                    "&ptopt=1&style=40"
            connection = URI.create(builder).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.setRequestProperty(
                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
            connection.setRequestProperty("Referer", buildXloginUrl())
            connection.setRequestProperty("Accept", "*/*")
            val cookieHeader = buildCookieHeader(AUTH_SESSION_COOKIES)
            if (cookieHeader.isNotBlank()) {
                connection.setRequestProperty("Cookie", cookieHeader)
            }
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val responseCode = connection.responseCode
            val location = connection.getHeaderField("Location")
            val refresh = connection.getHeaderField("Refresh")
            val body = if (responseCode >= 400 || location == null) safeReadResponse(connection) else ""
            captureCookies(connection, AUTH_SESSION_COOKIES)
            connection.disconnect()
            var authCode = extractCode(location, refresh)
            if (authCode.isNullOrBlank()) {
                authCode = extractCallbackCode(body)
            }
            AcademyCraft.LOGGER.info(
                "QQ music login stage ptlogin_jump code={}, nextLocation={}, hasRefresh={}, cookies={}, body={}",
                responseCode, location, refresh != null, AUTH_SESSION_COOKIES.keys, summarize(body)
            )
            if (!authCode.isNullOrBlank()) {
                return authCode
            }
            var nextUrl = location
            if (nextUrl.isNullOrBlank()) {
                nextUrl = extractNextUrl(body, refresh)
            }
            if (nextUrl.isNullOrBlank()) {
                return null
            }
            return followAuthorizeCodeRedirects(nextUrl)
        } catch (e: Exception) {
            AcademyCraft.LOGGER.error("QQ music login stage ptlogin_jump failed", e)
            return null
        } finally {
            connection?.disconnect()
        }
    }

    private fun requestXloginCode(): String? {
        var connection: HttpURLConnection? = null
        try {
            val xloginUrl = buildXloginUrl()
            connection = URI.create(xloginUrl).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.setRequestProperty(
                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
            connection.setRequestProperty(
                "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            connection.setRequestProperty("Referer", "https://graph.qq.com/")
            val cookieHeader = buildCookieHeader(AUTH_SESSION_COOKIES)
            if (cookieHeader.isNotBlank()) {
                connection.setRequestProperty("Cookie", cookieHeader)
            }
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            val responseCode = connection.responseCode
            val location = connection.getHeaderField("Location")
            val refresh = connection.getHeaderField("Refresh")
            val body = if (responseCode >= 400 || location == null) safeReadResponse(connection) else ""
            captureCookies(connection, AUTH_SESSION_COOKIES)
            connection.disconnect()
            var authCode = extractCode(location, refresh)
            if (authCode.isNullOrBlank()) {
                authCode = extractCallbackCode(body)
            }
            AcademyCraft.LOGGER.info(
                "QQ music login stage xlogin code={}, nextLocation={}, hasRefresh={}, cookies={}, body={}",
                responseCode, location, refresh != null, AUTH_SESSION_COOKIES.keys, summarize(body)
            )
            if (!authCode.isNullOrBlank()) {
                return authCode
            }
            var nextUrl = location
            if (nextUrl.isNullOrBlank()) {
                nextUrl = extractNextUrl(body, refresh)
            }
            if (nextUrl.isNullOrBlank()) {
                return null
            }
            return followAuthorizeCodeRedirects(nextUrl)
        } catch (e: Exception) {
            AcademyCraft.LOGGER.error("QQ music login stage xlogin failed", e)
            return null
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildXloginUrl(): String = XLOGIN_URL + "?appid=" + APPID +
            "&daid=383" +
            "&style=33" +
            "&login_text=" + URLEncoder.encode("登录", StandardCharsets.UTF_8) +
            "&hide_title_bar=1" +
            "&hide_border=1" +
            "&target=self" +
            "&s_url=" + URLEncoder.encode(LOGIN_JUMP_URL, StandardCharsets.UTF_8) +
            "&pt_3rd_aid=" + THIRD_APPID +
            "&pt_feedback_link=" +
            URLEncoder.encode(
                "https://support.qq.com/products/77942?customInfo=.appid$THIRD_APPID", StandardCharsets.UTF_8
            ) +
            "&theme=2&verify_theme="

    private fun clearAuthSessionCookies() {
        AUTH_SESSION_COOKIES.clear()
    }

    private fun safeReadResponse(connection: HttpURLConnection): String =
        try {
            readResponse(connection)
        } catch (_: Exception) {
            ""
        }

    private fun summarize(text: String?): String {
        if (text == null) {
            return ""
        }
        val normalized = text.replace('\n', ' ').replace('\r', ' ').trim()
        if (normalized.length <= 240) {
            return normalized
        }
        return normalized.substring(0, 240) + "..."
    }

    private fun extractCookieValue(cookie: String, key: String): String? {
        val prefix = "$key="
        for (part in cookie.split(";")) {
            val trimmed = part.trim()
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length)
            }
        }
        return null
    }

    private fun extractCode(vararg candidates: String?): String? {
        for (candidate in candidates) {
            if (candidate.isNullOrBlank()) {
                continue
            }
            val match = CODE_PATTERN.find(candidate)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    private fun extractPortalCode(vararg candidates: String?): String? {
        for (candidate in candidates) {
            if (candidate.isNullOrBlank()) {
                continue
            }
            val match = PORTAL_CODE_PATTERN.find(candidate)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    private fun extractNumericField(body: String?, fieldName: String?): String? {
        if (body.isNullOrBlank() || fieldName.isNullOrBlank()) {
            return null
        }
        val quotedPattern = Regex(Regex.escape(fieldName) + "[\"']?\\s*[:=]\\s*[\"']?(\\d+)")
        quotedPattern.find(body)?.let { return it.groupValues[1] }
        val callbackPattern = Regex(Regex.escape(fieldName) + ".*?(\\d+)")
        callbackPattern.find(body)?.let { return it.groupValues[1] }
        return NUMERIC_PATTERN.find(body)?.groupValues?.get(1)
    }

    private fun extractCallbackCode(body: String?): String? {
        if (body.isNullOrBlank()) {
            return null
        }
        CALLBACK_CODE_PATTERN.find(body)?.let { return it.groupValues[1] }
        CALLBACK_URL_PATTERN.find(body)?.let { return extractCode(it.value) }
        return null
    }

    private fun normalizeUin(rawUin: String?): String? {
        if (rawUin.isNullOrBlank()) {
            return rawUin
        }
        val match = NUMERIC_PATTERN.find(rawUin)
        return if (match != null) match.groupValues[1] else rawUin
    }

    private fun appendFormField(builder: StringBuilder, key: String, value: String) {
        if (builder.isNotEmpty()) {
            builder.append('&')
        }
        builder.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
        builder.append('=')
        builder.append(URLEncoder.encode(value, StandardCharsets.UTF_8))
    }

    private fun firstNonBlank(vararg values: String?): String? {
        for (value in values) {
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return null
    }

    private fun extractNextUrl(body: String?, refresh: String?): String? {
        if (!refresh.isNullOrBlank()) {
            META_REFRESH_PATTERN.find(refresh)?.let { return it.groupValues[1] }
        }
        if (body.isNullOrBlank()) {
            return null
        }
        PTUI_REDIRECT_PATTERN.find(body)?.let { return it.groupValues[1] }
        JS_URL_PATTERN.find(body)?.let { return it.groupValues[1] }
        for (match in URL_PATTERN.findAll(body)) {
            val url = match.value
            if (url.contains("code=") || url.contains("oauth2.0/show") || url.contains("common_login.html")) {
                return url
            }
        }
        return null
    }

    private fun captureCookies(connection: HttpURLConnection?, target: MutableMap<String, String>?) {
        if (connection == null || target == null) {
            return
        }
        for ((key0, values) in connection.headerFields) {
            if (!"Set-Cookie".equals(key0, ignoreCase = true)) {
                continue
            }
            for (cookieStr in values) {
                val separator = cookieStr.indexOf('=')
                if (separator <= 0) {
                    continue
                }
                val key = cookieStr.substring(0, separator).trim()
                val value = extractCookieValue(cookieStr, key)
                if (!value.isNullOrBlank()) {
                    target[key] = value
                }
            }
        }
    }

    private fun buildCookieHeader(cookies: Map<String, String>?): String {
        if (cookies.isNullOrEmpty()) {
            return ""
        }
        val builder = StringBuilder()
        for ((key, value) in cookies) {
            if (key.isBlank() || value.isBlank()) {
                continue
            }
            if (builder.isNotEmpty()) {
                builder.append("; ")
            }
            builder.append(key).append('=').append(value)
        }
        return builder.toString()
    }

    private fun buildCookieHeader(vararg parts: String?): String {
        val builder = StringBuilder()
        for (part in parts) {
            if (part.isNullOrBlank()) {
                continue
            }
            if (builder.isNotEmpty()) {
                builder.append("; ")
            }
            builder.append(part)
        }
        return builder.toString()
    }

    private fun calculatePtqrtoken(qrsig: String): Long {
        var value = 0L
        for (i in qrsig.indices) {
            value += (value shl 5) + qrsig[i].code
            value = value and 0x7FFFFFFFL
        }
        return value
    }

    private fun calculateGtk(skey: String): Long {
        var hash = 5381L
        for (i in skey.indices) {
            hash += (hash shl 5) + skey[i].code
        }
        return hash and 0x7fffffffL
    }

    enum class LoginState {
        IDLE,
        WAITING_SCAN,
        SUCCESS,
        FAILED,
        QR_EXPIRED,
    }

    internal enum class CredentialSource {
        JSON,
        COOKIE,
        NONE,
    }

    class QrCodeSession(val imageBytes: ByteArray, val qrsig: String)

    internal data class LoginExchangeResult(
        val rootCode: Int,
        val serviceCode: Int,
        val responseKey: String,
        val credentialSource: CredentialSource,
        val credential: QqCredential?,
    )

    private data class PtLocalSession(val port: Int, val localTk: String, val keyIndex: String)
}
