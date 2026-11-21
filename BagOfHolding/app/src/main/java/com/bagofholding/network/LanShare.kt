package com.bagofholding.network

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URL

/** "192.168.1.23:47321" */
data class HostAddress(val host: String, val port: Int) {
    override fun toString(): String = "$host:$port"
}

object AddressParser {
    private const val MAX_PORT = 65_535

    /** Parses "192.168.1.5", "192.168.1.5:47321" or "http://192.168.1.5:47321/". Null if invalid. */
    fun parse(text: String, defaultPort: Int = LanShareServer.DEFAULT_PORT): HostAddress? {
        val trimmed = text.trim().removePrefix("http://").substringBefore('/')
        val hasPort = trimmed.count { it == ':' } == 1
        val host = if (hasPort) trimmed.substringBefore(':') else trimmed
        val port = if (hasPort) trimmed.substringAfter(':').toIntOrNull() else defaultPort
        val validPort = port?.takeIf { it in 1..MAX_PORT } ?: return null
        val validHost = host.isNotBlank() && host.none { it.isWhitespace() }
        return if (validHost) HostAddress(host, validPort) else null
    }
}

data class HttpRequest(val method: String, val path: String)

data class HttpResponse(val status: Int, val reason: String, val contentType: String, val body: String) {

    fun toBytes(): ByteArray {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: $contentType; charset=utf-8\r\n" +
            "Content-Length: ${bodyBytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        return head.toByteArray(Charsets.US_ASCII) + bodyBytes
    }

    companion object {
        const val OK = 200
        const val BAD_REQUEST = 400
        const val NOT_FOUND = 404
        const val METHOD_NOT_ALLOWED = 405
        const val SERVER_ERROR = 500

        fun text(status: Int, reason: String, body: String) = HttpResponse(status, reason, "text/plain", body)
        fun json(body: String) = HttpResponse(OK, "OK", "application/json", body)
    }
}

/** Just enough HTTP/1.1 to serve one JSON document to another phone. */
object HttpProtocol {
    private const val MAX_HEADER_LINES = 100

    /** Reads the request line and skips the headers. Returns null for malformed requests. */
    fun readRequest(input: InputStream): HttpRequest? {
        val reader = BufferedReader(InputStreamReader(input, Charsets.US_ASCII))
        val parts = reader.readLine()?.trim()?.split(' ').orEmpty()
        if (parts.size != 3 || !parts[2].startsWith("HTTP/")) return null
        var headerLines = 0
        while (headerLines < MAX_HEADER_LINES) {
            val line = reader.readLine()
            if (line.isNullOrEmpty()) break
            headerLines++
        }
        return HttpRequest(method = parts[0].uppercase(), path = parts[1].substringBefore('?'))
    }
}

/**
 * Minimal HTTP server that runs on the sharing phone. Each request to /inventory
 * reads the latest inventory from the database, so changes show up immediately.
 */
class LanShareServer(
    private val payloadProvider: suspend () -> String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    @Volatile
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    val isRunning: Boolean get() = serverSocket?.isClosed == false

    /** Binds the server socket and starts accepting clients in [scope]. Returns the bound port. */
    suspend fun start(scope: CoroutineScope, preferredPort: Int = DEFAULT_PORT): Int {
        stop()
        val socket = withContext(io) { bind(preferredPort) }
        serverSocket = socket
        acceptJob = scope.launch(io) { acceptLoop(socket) }
        return socket.localPort
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        val socket = serverSocket
        serverSocket = null
        try {
            socket?.close() // unblocks accept()
        } catch (ignored: IOException) {
            // Already closed.
        }
    }

    private fun bind(port: Int): ServerSocket {
        val socket = ServerSocket()
        socket.reuseAddress = true
        return try {
            socket.bind(InetSocketAddress(port))
            socket
        } catch (ignored: IOException) {
            // Preferred port is busy: let the OS choose a free one.
            socket.close()
            ServerSocket(0)
        }
    }

    private suspend fun acceptLoop(socket: ServerSocket) = coroutineScope {
        while (isActive && !socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (ignored: IOException) {
                break // socket closed by stop()
            }
            launch { serve(client) }
        }
    }

    private suspend fun serve(client: Socket) {
        try {
            client.use { socket ->
                socket.soTimeout = CLIENT_TIMEOUT_MS
                val request = HttpProtocol.readRequest(socket.getInputStream())
                val response = route(request, payloadProvider)
                socket.getOutputStream().apply {
                    write(response.toBytes())
                    flush()
                }
            }
        } catch (ignored: IOException) {
            // The other phone disconnected or timed out; nothing to clean up.
        }
    }

    companion object {
        const val DEFAULT_PORT = 47_321
        const val PATH_INVENTORY = "/inventory"
        const val PATH_PING = "/ping"
        private const val CLIENT_TIMEOUT_MS = 5_000

        internal suspend fun route(request: HttpRequest?, payload: suspend () -> String): HttpResponse = when {
            request == null -> HttpResponse.text(HttpResponse.BAD_REQUEST, "Bad Request", "Bad request")
            request.method != "GET" ->
                HttpResponse.text(HttpResponse.METHOD_NOT_ALLOWED, "Method Not Allowed", "Only GET is supported")
            request.path == PATH_PING -> HttpResponse.text(HttpResponse.OK, "OK", SharePayload.APP_ID)
            request.path == PATH_INVENTORY -> runCatching { payload() }.fold(
                onSuccess = { HttpResponse.json(it) },
                onFailure = {
                    HttpResponse.text(HttpResponse.SERVER_ERROR, "Internal Server Error", "Inventory unavailable")
                },
            )
            else -> HttpResponse.text(HttpResponse.NOT_FOUND, "Not Found", "Not found")
        }
    }
}

/** Downloads a shared inventory. An interface so ViewModel tests can use a fake. */
fun interface ShareFetcher {
    @Throws(IOException::class, ShareFormatException::class)
    suspend fun fetch(address: HostAddress): SharePayload
}

/** Downloads an inventory from another phone running [LanShareServer]. */
class LanShareClient(private val io: CoroutineDispatcher = Dispatchers.IO) : ShareFetcher {

    override suspend fun fetch(address: HostAddress): SharePayload = withContext(io) {
        val url = URL("http", address.host, address.port, LanShareServer.PATH_INVENTORY)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "GET"
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("Server answered HTTP $code")
            val body = connection.inputStream.use { it.readLimited(MAX_RESPONSE_BYTES) }
            ShareCodec.decode(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun InputStream.readLimited(limit: Int): String {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = read(chunk)
            if (count < 0) break
            total += count
            if (total > limit) throw IOException("Response is too large")
            buffer.write(chunk, 0, count)
        }
        return buffer.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val TIMEOUT_MS = 5_000
        const val BUFFER_SIZE = 8 * 1024
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    }
}

object NetworkUtils {
    /** IPv4 addresses of this phone on Wi-Fi / hotspot interfaces, Wi-Fi first. */
    fun localIpv4Addresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
    } catch (ignored: SocketException) {
        emptyList()
    }
}
