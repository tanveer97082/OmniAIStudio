package com.omniai.studio.lan

import com.omniai.studio.data.local.OmniAiDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class LanWebServerState(
    val isRunning: Boolean = false,
    val hostIp: String = "127.0.0.1",
    val port: Int = 8765,
    val accessPin: String = "",
    val servedRequests: Int = 0,
    val lastError: String? = null
) {
    val url: String get() = "http://$hostIp:$port"
}

/**
 * v5.0 Local Wi-Fi LAN Web Studio Server:
 * Zero-dependency HTTP/1.1 companion server built strictly on `java.net.ServerSocket`.
 * Protected by a cryptographically random 6-digit PIN (`SecureRandom`) validated in constant time
 * (`MessageDigest.isEqual`) with per-client IP rate limiting and strict HTML escaping.
 */
class LocalLanWebServer(
    private val db: OmniAiDatabase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val secureRandom = SecureRandom()
    private val requestCounter = AtomicInteger(0)
    private val failedAttemptsByIp = ConcurrentHashMap<String, Pair<Int, Long>>()

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    private val _state = MutableStateFlow(LanWebServerState())
    val state: StateFlow<LanWebServerState> = _state.asStateFlow()

    @Synchronized
    fun start(port: Int = DEFAULT_PORT): LanWebServerState {
        if (_state.value.isRunning && serverSocket != null && serverSocket?.isClosed == false) {
            return _state.value
        }
        stop()

        val safePort = port.coerceIn(1024, 65535)
        val pin = generateSixDigitPin()
        val lanIp = resolveLocalIpv4Address()

        return try {
            val socket = ServerSocket(safePort).apply {
                reuseAddress = true
                soTimeout = 0
            }
            serverSocket = socket
            requestCounter.set(0)
            failedAttemptsByIp.clear()

            val runningState = LanWebServerState(
                isRunning = true,
                hostIp = lanIp,
                port = safePort,
                accessPin = pin,
                servedRequests = 0,
                lastError = null
            )
            _state.value = runningState

            acceptJob = scope.launch {
                while (isActive && !socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    launch { handleClientSocket(client, pin) }
                }
            }
            runningState
        } catch (ex: Exception) {
            val errorState = LanWebServerState(
                isRunning = false,
                hostIp = lanIp,
                port = safePort,
                accessPin = "",
                lastError = "Failed to bind port $safePort: ${ex.message}"
            )
            _state.value = errorState
            errorState
        }
    }

    @Synchronized
    fun stop(): LanWebServerState {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        val stopped = _state.value.copy(
            isRunning = false,
            accessPin = "",
            lastError = null
        )
        _state.value = stopped
        return stopped
    }

    private suspend fun handleClientSocket(client: Socket, expectedPin: String) {
        client.use { socket ->
            socket.soTimeout = 8_000
            val clientIp = socket.inetAddress?.hostAddress ?: "unknown"
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                writeResponse(socket, 400, "text/plain; charset=utf-8", "Bad Request")
                return
            }

            val method = parts[0].uppercase()
            val fullTarget = parts[1]
            val path = fullTarget.substringBefore("?")
            val queryParams = parseQueryParams(fullTarget.substringAfter("?", ""))

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    headers[line.substring(0, colonIdx).trim().lowercase()] =
                        line.substring(colonIdx + 1).trim()
                }
            }

            if (method != "GET") {
                writeResponse(socket, 405, "text/plain; charset=utf-8", "Method Not Allowed")
                return
            }

            // Serve unauthenticated PIN login shell at "/" when no PIN is supplied
            val suppliedPin = headers["x-omni-pin"] ?: queryParams["pin"].orEmpty()
            if (path == "/" && suppliedPin.isBlank()) {
                writeResponse(socket, 200, "text/html; charset=utf-8", buildPinLoginHtml())
                return
            }

            if (isIpRateLimited(clientIp)) {
                writeResponse(
                    socket,
                    429,
                    "application/json; charset=utf-8",
                    """{"error":"Too many failed PIN attempts. Wait 60 seconds and retry."}"""
                )
                return
            }

            if (!verifyPinConstantTime(expectedPin, suppliedPin)) {
                recordFailedAttempt(clientIp)
                writeResponse(
                    socket,
                    401,
                    "application/json; charset=utf-8",
                    """{"error":"Invalid 6-digit Studio PIN."}"""
                )
                return
            }

            failedAttemptsByIp.remove(clientIp)
            val count = requestCounter.incrementAndGet()
            _state.value = _state.value.copy(servedRequests = count)

            when (path) {
                "/" -> {
                    val html = buildAuthenticatedStudioHtml(suppliedPin)
                    writeResponse(socket, 200, "text/html; charset=utf-8", html)
                }
                "/api/status" -> {
                    val payload = buildJsonObject {
                        put("status", JsonPrimitive("online"))
                        put("chats", JsonPrimitive(db.chatDao().countThreads()))
                        put("messages", JsonPrimitive(db.chatDao().countMessages()))
                        put("scripts", JsonPrimitive(db.videoScriptDao().countScripts()))
                        put("projects", JsonPrimitive(db.projectDao().countProjects()))
                        put("niches", JsonPrimitive(db.nicheDao().countNiches()))
                    }
                    writeResponse(socket, 200, "application/json; charset=utf-8", payload.toString())
                }
                "/api/chats" -> {
                    val threads = db.chatDao().getAllThreadsOnce().take(30)
                    val arr: JsonArray = buildJsonArray {
                        threads.forEach { t ->
                            add(
                                buildJsonObject {
                                    put("id", JsonPrimitive(t.id))
                                    put("title", JsonPrimitive(t.title))
                                    put("folder", JsonPrimitive(t.folderName))
                                    put("messages", JsonPrimitive(t.messageCount))
                                    put("provider", JsonPrimitive(t.providerUsed))
                                }
                            )
                        }
                    }
                    writeResponse(socket, 200, "application/json; charset=utf-8", arr.toString())
                }
                "/api/scripts" -> {
                    val scripts = db.videoScriptDao().getAllScriptsWithRelationsOnce().map { it.script }.take(30)
                    val arr: JsonArray = buildJsonArray {
                        scripts.forEach { s ->
                            add(
                                buildJsonObject {
                                    put("id", JsonPrimitive(s.id))
                                    put("title", JsonPrimitive(s.title))
                                    put("topic", JsonPrimitive(s.topic))
                                    put("aspectRatio", JsonPrimitive(s.aspectRatio))
                                    put("status", JsonPrimitive(s.status))
                                }
                            )
                        }
                    }
                    writeResponse(socket, 200, "application/json; charset=utf-8", arr.toString())
                }
                else -> {
                    writeResponse(socket, 404, "application/json; charset=utf-8", """{"error":"Endpoint not found"}""")
                }
            }
        }
    }

    private fun buildPinLoginHtml(): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>OmniAI Studio — Local Wi-Fi Web Bridge</title>
          <style>
            body{background:#09090C;color:#F4F4F8;font-family:system-ui,sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;padding:16px}
            .card{background:#13131A;border:1px solid #232330;border-radius:14px;padding:24px;max-width:380px;width:100%}
            input{width:100%;box-sizing:border-box;background:#1B1B25;border:1px solid #2C2C3E;color:#fff;padding:12px;border-radius:10px;font-size:18px;letter-spacing:4px;text-align:center}
            button{width:100%;margin-top:12px;background:#00E676;color:#09090C;font-weight:700;border:0;border-radius:10px;padding:12px;font-size:14px;cursor:pointer}
            p{color:#9CA3AF;font-size:13px}
          </style>
        </head>
        <body>
          <div class="card">
            <h2>OmniAI Local Wi-Fi Studio</h2>
            <p>Enter the 6-digit pairing PIN displayed on your Android phone.</p>
            <form method="GET" action="/">
              <input name="pin" type="password" inputmode="numeric" maxlength="6" placeholder="000000" required />
              <button type="submit">Unlock Local Studio</button>
            </form>
          </div>
        </body>
        </html>
    """.trimIndent()

    private suspend fun buildAuthenticatedStudioHtml(pin: String): String {
        val threads = db.chatDao().getAllThreadsOnce().take(15)
        val scripts = db.videoScriptDao().getAllScriptsWithRelationsOnce().map { it.script }.take(15)
        val projects = db.projectDao().getAllProjectsOnce().take(15)

        val chatsHtml = if (threads.isEmpty()) {
            "<p class='muted'>No chat threads yet.</p>"
        } else {
            threads.joinToString("") { t ->
                "<div class='row'><strong>${escapeHtml(t.title)}</strong><span>${t.messageCount} msgs · ${escapeHtml(t.folderName)}</span></div>"
            }
        }

        val scriptsHtml = if (scripts.isEmpty()) {
            "<p class='muted'>No video scripts yet.</p>"
        } else {
            scripts.joinToString("") { s ->
                "<div class='row'><strong>${escapeHtml(s.title)}</strong><span>${escapeHtml(s.aspectRatio)} · ${escapeHtml(s.status)}</span></div>"
            }
        }

        val projectsHtml = if (projects.isEmpty()) {
            "<p class='muted'>No code projects yet.</p>"
        } else {
            projects.joinToString("") { p ->
                "<div class='row'><strong>${escapeHtml(p.name)}</strong><span>${escapeHtml(p.githubOwner)}/${escapeHtml(p.githubRepo)} · ${escapeHtml(p.status)}</span></div>"
            }
        }

        val safePinParam = escapeHtml(pin)
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>OmniAI Local Wi-Fi Web Studio</title>
              <style>
                body{background:#09090C;color:#F4F4F8;font-family:system-ui,sans-serif;margin:0;padding:20px}
                .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:16px;max-width:1100px;margin:0 auto}
                .card{background:#13131A;border:1px solid #232330;border-radius:14px;padding:16px}
                .row{display:flex;justify-content:space-between;gap:12px;padding:8px 0;border-bottom:1px solid #232330;font-size:13px}
                .row span,.muted{color:#9CA3AF;font-size:12px}
                a{color:#00E676;text-decoration:none;font-size:12px;margin-right:12px}
              </style>
            </head>
            <body>
              <div style="max-width:1100px;margin:0 auto 16px auto;display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px">
                <div>
                  <h2 style="margin:0">OmniAI-Studio · Local Wi-Fi Bridge</h2>
                  <div class="muted">Zero-dependency LAN mirror over local Wi-Fi</div>
                </div>
                <div>
                  <a href="/api/status?pin=$safePinParam">JSON Status</a>
                  <a href="/api/chats?pin=$safePinParam">JSON Chats</a>
                  <a href="/api/scripts?pin=$safePinParam">JSON Scripts</a>
                </div>
              </div>
              <div class="grid">
                <div class="card"><h3>Recent Chats</h3>$chatsHtml</div>
                <div class="card"><h3>Video Scripts</h3>$scriptsHtml</div>
                <div class="card"><h3>Projects</h3>$projectsHtml</div>
              </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun writeResponse(socket: Socket, statusCode: Int, contentType: String, body: String) {
        val statusText = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            429 -> "Too Many Requests"
            else -> "Error"
        }
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("X-Frame-Options: DENY\r\n")
            append("Connection: close\r\n\r\n")
        }
        val out = socket.getOutputStream()
        out.write(headers.toByteArray(Charsets.UTF_8))
        out.write(bodyBytes)
        out.flush()
    }

    private fun generateSixDigitPin(): String {
        val value = secureRandom.nextInt(1_000_000)
        return "%06d".format(value)
    }

    private fun verifyPinConstantTime(expected: String, supplied: String): Boolean {
        if (expected.isEmpty() || supplied.isEmpty()) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            supplied.trim().toByteArray(Charsets.UTF_8)
        )
    }

    private fun isIpRateLimited(ip: String): Boolean {
        val (attempts, lastFailedMs) = failedAttemptsByIp[ip] ?: return false
        if (System.currentTimeMillis() - lastFailedMs > LOCKOUT_WINDOW_MS) {
            failedAttemptsByIp.remove(ip)
            return false
        }
        return attempts >= MAX_FAILED_PIN_ATTEMPTS
    }

    private fun recordFailedAttempt(ip: String) {
        val now = System.currentTimeMillis()
        val prev = failedAttemptsByIp[ip]
        val nextCount = if (prev != null && now - prev.second <= LOCKOUT_WINDOW_MS) prev.first + 1 else 1
        failedAttemptsByIp[ip] = nextCount to now
    }

    private fun parseQueryParams(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        val out = mutableMapOf<String, String>()
        rawQuery.split("&").forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = runCatching { URLDecoder.decode(pair.substring(0, idx), "UTF-8") }.getOrDefault("")
                val value = runCatching { URLDecoder.decode(pair.substring(idx + 1), "UTF-8") }.getOrDefault("")
                if (key.isNotBlank()) out[key] = value
            }
        }
        return out
    }

    private fun resolveLocalIpv4Address(): String {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        val host = addr.hostAddress
                        if (!host.isNullOrBlank()) return@runCatching host
                    }
                }
            }
            "127.0.0.1"
        }.getOrDefault("127.0.0.1")
    }

    private fun escapeHtml(raw: String): String = buildString(raw.length) {
        for (ch in raw) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }

    companion object {
        const val DEFAULT_PORT = 8765
        private const val MAX_FAILED_PIN_ATTEMPTS = 8
        private const val LOCKOUT_WINDOW_MS = 60_000L
    }
}
