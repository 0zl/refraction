package shiro.refraction.domain

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import shiro.refraction.data.model.ProxySettings
import shiro.refraction.data.model.ProxyType
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executor
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class ProxyManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val mainExecutor: Executor = Executor { command ->
        Handler(Looper.getMainLooper()).post(command)
    }

    private val sslSocketFactory: SSLSocketFactory by lazy {
        SSLContext.getInstance("TLS").apply { init(null, null, null) }.socketFactory
    }

    fun load(): ProxySettings {
        if (!prefs.getBoolean(KEY_ENABLED, false)) return ProxySettings()
        return ProxySettings(
            enabled = true,
            type = if (prefs.getString(KEY_TYPE, null) == TYPE_HTTP) ProxyType.HTTP else ProxyType.SOCKS5,
            host = prefs.getString(KEY_HOST, "").orEmpty(),
            port = prefs.getInt(KEY_PORT, DEFAULT_SOCKS_PORT),
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
            password = prefs.getString(KEY_PASSWORD, "").orEmpty()
        )
    }

    fun save(settings: ProxySettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_TYPE, if (settings.type == ProxyType.HTTP) TYPE_HTTP else TYPE_SOCKS5)
            .putString(KEY_HOST, settings.host)
            .putInt(KEY_PORT, settings.port)
            .putString(KEY_USERNAME, settings.username)
            .putString(KEY_PASSWORD, settings.password)
            .apply()
    }

    fun isSupported(): Boolean = try {
        WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
    } catch (e: Exception) {
        false
    }

    /** Applies or clears the process-wide WebView proxy override. Callback runs on the main thread. */
    fun apply(settings: ProxySettings, onApplied: (Boolean) -> Unit) {
        if (!isSupported()) {
            onApplied(false)
            return
        }
        val controller = ProxyController.getInstance()
        val listener = Runnable { onApplied(true) }
        if (!settings.enabled || settings.host.isBlank()) {
            controller.clearProxyOverride(mainExecutor, listener)
            return
        }
        val scheme = if (settings.type == ProxyType.HTTP) "http" else "socks5"
        val config = ProxyConfig.Builder()
            .addProxyRule("$scheme://${settings.host}:${settings.port}", ProxyConfig.MATCH_ALL_SCHEMES)
            .build()
        try {
            controller.setProxyOverride(config, mainExecutor, listener)
        } catch (e: IllegalArgumentException) {
            onApplied(false)
        }
    }

    data class TestResult(val ok: Boolean, val message: String, val exitIp: String? = null)

    /**
     * Verifies the proxy by opening a tunnel to api.ipify.org and reading the exit IP.
     * HTTP proxies use CONNECT, SOCKS5 uses a full RFC 1928 handshake (with auth when given).
     */
    suspend fun test(settings: ProxySettings): TestResult = withContext(Dispatchers.IO) {
        val host = settings.host.trim()
        val port = settings.port
        if (host.isBlank() || port !in 1..65535) {
            return@withContext TestResult(false, "Invalid host or port")
        }
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS

            val out = socket.getOutputStream()
            val input = socket.getInputStream()

            val tunneled = if (settings.type == ProxyType.HTTP) {
                httpConnect(out, input, TEST_HOST, TEST_PORT, settings.username, settings.password)
            } else {
                socks5Connect(out, input, TEST_HOST, TEST_PORT, settings.username, settings.password)
            }
            if (!tunneled) {
                return@withContext TestResult(false, "Proxy refused the connection to $TEST_HOST")
            }

            val ssl = sslSocketFactory
                .createSocket(socket, TEST_HOST, TEST_PORT, true) as SSLSocket
            ssl.soTimeout = READ_TIMEOUT_MS
            ssl.startHandshake()

            val sslOut = BufferedOutputStream(ssl.getOutputStream())
            val sslIn = BufferedInputStream(ssl.getInputStream())
            sslOut.write(("GET / HTTP/1.1\r\nHost: $TEST_HOST\r\nConnection: close\r\n\r\n").toByteArray())
            sslOut.flush()

            val headers = readHeaderLines(sslIn)
            if (!statusIsOk(headers)) {
                return@withContext TestResult(false, "Tunnel responded with an error")
            }
            val contentLength = headers
                .firstNotNullOfOrNull { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2 && parts[0].trim().equals("Content-Length", ignoreCase = true)) {
                        parts[1].trim().toIntOrNull()
                    } else {
                        null
                    }
                }
                ?.coerceAtMost(MAX_BODY_BYTES)
            val body = if (contentLength != null) {
                val bytes = ByteArray(contentLength)
                if (!readFully(sslIn, bytes)) "" else String(bytes, Charsets.UTF_8)
            } else {
                sslIn.readBytes().toString(Charsets.UTF_8)
            }
            val ip = body.trim().takeIf { it.matches(IP_REGEX) }
            TestResult(true, "Connected through proxy", ip)
        } catch (e: SocketTimeoutException) {
            TestResult(false, "Timed out")
        } catch (e: Exception) {
            TestResult(false, e.message ?: e.javaClass.simpleName)
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun httpConnect(
        out: java.io.OutputStream,
        input: InputStream,
        host: String,
        port: Int,
        username: String,
        password: String
    ): Boolean {
        val request = StringBuilder()
        request.append("CONNECT $host:$port HTTP/1.1\r\n")
        request.append("Host: $host:$port\r\n")
        if (username.isNotEmpty()) {
            val token = Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)
            request.append("Proxy-Authorization: Basic $token\r\n")
        }
        request.append("\r\n")
        out.write(request.toString().toByteArray())
        out.flush()
        return statusIsOk(readHeaderLines(input))
    }

    private fun socks5Connect(
        out: java.io.OutputStream,
        input: InputStream,
        host: String,
        port: Int,
        username: String,
        password: String
    ): Boolean {
        val methods = if (username.isNotEmpty()) {
            byteArrayOf(0x05, 0x02, 0x00, 0x02)
        } else {
            byteArrayOf(0x05, 0x01, 0x00)
        }
        out.write(methods)
        out.flush()

        val greeting = ByteArray(2)
        if (!readFully(input, greeting) || greeting[0] != 0x05.toByte()) return false
        when (greeting[1]) {
            0x00.toByte() -> { /* no auth required */ }
            0x02.toByte() -> {
                val user = username.toByteArray(Charsets.UTF_8)
                val pass = password.toByteArray(Charsets.UTF_8)
                if (user.size > 255 || pass.size > 255) return false
                val auth = ByteArray(1 + 1 + user.size + 1 + pass.size)
                auth[0] = 0x01
                auth[1] = user.size.toByte()
                System.arraycopy(user, 0, auth, 2, user.size)
                auth[2 + user.size] = pass.size.toByte()
                System.arraycopy(pass, 0, auth, 3 + user.size, pass.size)
                out.write(auth)
                out.flush()
                val authReply = ByteArray(2)
                if (!readFully(input, authReply) || authReply[1] != 0x00.toByte()) return false
            }
            else -> return false
        }

        val hostBytes = host.toByteArray(Charsets.UTF_8)
        if (hostBytes.size > 255) return false
        val connect = ByteArray(5 + hostBytes.size + 2)
        connect[0] = 0x05
        connect[1] = 0x01
        connect[2] = 0x00
        connect[3] = 0x03
        connect[4] = hostBytes.size.toByte()
        System.arraycopy(hostBytes, 0, connect, 5, hostBytes.size)
        connect[5 + hostBytes.size] = (port shr 8).toByte()
        connect[6 + hostBytes.size] = port.toByte()
        out.write(connect)
        out.flush()

        val reply = ByteArray(4)
        if (!readFully(input, reply) || reply[0] != 0x05.toByte() || reply[1] != 0x00.toByte()) return false
        return when (reply[3]) {
            0x01.toByte() -> skipFully(input, 4) && skipFully(input, 2)
            0x03.toByte() -> {
                val len = input.read()
                len >= 0 && skipFully(input, len) && skipFully(input, 2)
            }
            0x04.toByte() -> skipFully(input, 16) && skipFully(input, 2)
            else -> false
        }
    }

    private fun statusIsOk(lines: List<String>): Boolean {
        val parts = lines.firstOrNull()?.split(" ") ?: return false
        return parts.size >= 2 && parts[1] == "200"
    }

    /** Reads header lines byte-by-byte so no tunneled data is consumed by read-ahead. */
    private fun readHeaderLines(input: InputStream): List<String> {
        val lines = mutableListOf<String>()
        val line = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) break
            if (b == '\n'.code) {
                val trimmed = line.toString().removeSuffix("\r")
                lines.add(trimmed)
                if (trimmed.isEmpty()) break
                line.setLength(0)
            } else {
                line.append(b.toChar())
            }
        }
        return lines
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }

    private fun skipFully(input: InputStream, count: Int): Boolean {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining.toLong())
            if (skipped <= 0) {
                if (input.read() < 0) return false
                remaining--
            } else {
                remaining -= skipped.toInt()
            }
        }
        return true
    }

    private companion object {
        const val PREFS_NAME = "refraction_prefs"
        const val KEY_ENABLED = "proxy_enabled"
        const val KEY_TYPE = "proxy_type"
        const val KEY_HOST = "proxy_host"
        const val KEY_PORT = "proxy_port"
        const val KEY_USERNAME = "proxy_username"
        const val KEY_PASSWORD = "proxy_password"
        const val TYPE_HTTP = "http"
        const val TYPE_SOCKS5 = "socks5"
        const val DEFAULT_SOCKS_PORT = 1080

        const val TEST_HOST = "api.ipify.org"
        const val TEST_PORT = 443
        const val CONNECT_TIMEOUT_MS = 6000
        const val READ_TIMEOUT_MS = 6000
        const val MAX_BODY_BYTES = 512

        val IP_REGEX = Regex("^[0-9a-fA-F:.]+$")
    }
}
