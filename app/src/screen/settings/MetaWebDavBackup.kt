package com.github.yumelira.yumebox.screen.settings

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import java.util.Base64
import java.util.Locale
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class MetaWebDavConfig(
    val url: String,
    val account: String,
    val password: String,
    val directory: String,
) {
    fun isValid(): Boolean {
        return url.isNotBlank() && account.isNotBlank() && password.isNotBlank()
    }
    fun normalizedRootUrl(): String {
        val base = url.trim().removeSuffix("/")
        val dir = directory.trim().trim('/').takeIf { it.isNotBlank() }
        val merged = if (dir == null) base else "$base/$dir"
        return "$merged/"
    }
}

internal data class MetaWebDavBackupFile(
    val name: String,
    val lastModified: Long,
)

internal object MetaWebDavBackup {
    private const val TEMP_DOWNLOAD_NAME = "meta_webdav_restore.zip"
    private const val EMPTY_XML = "<?xml version=\"1.0\"?><propfind xmlns=\"DAV:\"><prop><displayname/><getlastmodified/><resourcetype/></prop></propfind>"
    suspend fun test(config: MetaWebDavConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = WebDavClient(config)
            runCatching { client.ensureRootDirectory() }
            client.listBackups()
            Unit
        }
    }
    suspend fun backup(context: Context, config: MetaWebDavConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val client = WebDavClient(config)
            client.ensureRootDirectory()
            val tempFile = File(context.cacheDir, MetaBackupRestore.defaultBackupFileName())
            try {
                MetaBackupRestore.backupToFile(context, tempFile).getOrThrow()
                client.uploadBackup(tempFile)
                tempFile.name
            } finally {
                tempFile.delete()
            }
        }
    }
    suspend fun restoreLatest(context: Context, config: MetaWebDavConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val client = WebDavClient(config)
            client.ensureRootDirectory()
            val latest = client.listBackups().maxByOrNull { it.lastModified }
                ?: throw IllegalStateException("no remote backup found")
            val tempFile = File(context.cacheDir, TEMP_DOWNLOAD_NAME)
            try {
                client.downloadBackup(latest.name, tempFile)
                MetaBackupRestore.restoreFromFile(context, tempFile).getOrThrow()
                latest.name
            } finally {
                tempFile.delete()
            }
        }
    }
    /**
     * Lightweight WebDAV HTTP client that uses raw sockets, bypassing
     * Android's OkHttp-backed HttpURLConnection which rejects non-standard
     * HTTP methods like PROPFIND and MKCOL.
     */
    private class WebDavClient(config: MetaWebDavConfig) {
        private val rootUrl = config.normalizedRootUrl()
        private val authHeader = "Basic " + Base64.getEncoder().encodeToString(
            "${config.account.trim()}:${config.password}".toByteArray()
        )
        private val socketTimeout = 120_000
        fun ensureRootDirectory() {
            runCatching {
                val resp = rawRequest("MKCOL", rootUrl)
                if (resp.statusCode !in setOf(200, 201, 204, 207, 301, 302, 405, 409)) {
                    // MKCOL failed but directory may already exist; continue gracefully
                }
            }
            val resp = rawRequest("PROPFIND", rootUrl, body = EMPTY_XML.toByteArray(), extraHeaders = mapOf("Depth" to "0"))
            if (resp.statusCode !in setOf(200, 207)) {
                throw IllegalStateException("WebDAV directory not accessible: HTTP ${resp.statusCode}")
            }
        }
        fun listBackups(): List<MetaWebDavBackupFile> {
            val resp = rawRequest("PROPFIND", rootUrl, body = EMPTY_XML.toByteArray(), extraHeaders = mapOf("Depth" to "1"))
            if (resp.statusCode !in setOf(200, 207)) {
                throw IllegalStateException("PROPFIND failed with HTTP ${resp.statusCode}")
            }
            val xml = resp.bodyText()
            return parseListResponse(xml)
                .filter { it.name.endsWith(".zip", ignoreCase = true) }
        }
        fun uploadBackup(file: File) {
            val resp = rawRequest("PUT", rootUrl + file.name, body = file.readBytes(), contentType = "application/zip")
            if (resp.statusCode !in 200..299) {
                throw IllegalStateException("PUT failed with HTTP ${resp.statusCode}")
            }
        }
        fun downloadBackup(fileName: String, targetFile: File) {
            val resp = rawRequest("GET", rootUrl + fileName)
            if (resp.statusCode !in 200..299) {
                throw IllegalStateException("GET failed with HTTP ${resp.statusCode}")
            }
            targetFile.parentFile?.mkdirs()
            FileOutputStream(targetFile).use { it.write(resp.body) }
        }
        // -- Raw HTTP over socket ------------------------------------------------
        private class HttpResponse(val statusCode: Int, val body: ByteArray) {
            fun bodyText(): String = body.toString(StandardCharsets.UTF_8)
        }
        private fun rawRequest(
            method: String,
            url: String,
            body: ByteArray? = null,
            contentType: String? = null,
            extraHeaders: Map<String, String> = emptyMap(),
        ): HttpResponse {
            val uri = URI(url)
            val isTls = uri.scheme.equals("https", ignoreCase = true)
            val host = uri.host ?: throw IllegalArgumentException("Missing host in URL: $url")
            val defaultPort = if (isTls) 443 else 80
            val port = if (uri.port > 0) uri.port else defaultPort
            val path = if (uri.rawQuery != null) "${uri.rawPath}?${uri.rawQuery}" else uri.rawPath
            val socket: Socket = if (isTls) {
                SSLSocketFactory.getDefault().createSocket(host, port)
            } else {
                Socket(host, port)
            }
            socket.soTimeout = socketTimeout
            try {
                // TCP connect timeout is handled by the caller or OS; set a reasonable
                // value via socket options before connect if needed. Socket(host, port)
                // already blocks until connected.
                val out = socket.getOutputStream()
                val requestLine = "$method $path HTTP/1.1\r\n"
                out.write(requestLine.toByteArray(StandardCharsets.US_ASCII))
                // Required headers
                writeHeader(out, "Host", if (port == defaultPort) host else "$host:$port")
                writeHeader(out, "Authorization", authHeader)
                writeHeader(out, "Connection", "close")
                for ((k, v) in extraHeaders) writeHeader(out, k, v)
                if (body != null) {
                    writeHeader(out, "Content-Length", body.size.toString())
                    writeHeader(out, "Content-Type", contentType ?: "text/xml; charset=utf-8")
                }
                out.write("\r\n".toByteArray(StandardCharsets.US_ASCII))
                out.flush()
                if (body != null) {
                    out.write(body)
                    out.flush()
                }
                // Read status line
                val inputStream = socket.getInputStream()
                val statusLine = readLine(inputStream)
                val statusCode = parseStatusCode(statusLine)
                // Skip headers
                while (true) {
                    val line = readLine(inputStream)
                    if (line.isEmpty()) break
                }
                // Read body
                val bodyBytes = readUntilClose(inputStream)
                return HttpResponse(statusCode, bodyBytes)
            } finally {
                runCatching { socket.close() }
            }
        }
        private fun writeHeader(out: OutputStream, name: String, value: String) {
            out.write("$name: $value\r\n".toByteArray(StandardCharsets.US_ASCII))
        }
        private fun readLine(input: InputStream): String {
            val sb = StringBuilder()
            var prev = -1
            while (true) {
                val b = input.read()
                if (b == -1) break
                if (b == '\n'.code && prev == '\r'.code) {
                    sb.setLength(sb.length - 1) // remove '\r'
                    break
                }
                sb.append(b.toChar())
                prev = b
            }
            return sb.toString()
        }
        private fun parseStatusCode(statusLine: String): Int {
            // "HTTP/1.1 207 Multi-Status"
            val parts = statusLine.split(" ", limit = 3)
            return parts.getOrNull(1)?.toIntOrNull() ?: 0
        }
        private fun readUntilClose(input: InputStream): ByteArray {
            val buf = ByteArrayOutputStream(8192)
            val tmp = ByteArray(8192)
            while (true) {
                val n = input.read(tmp)
                if (n == -1) break
                buf.write(tmp, 0, n)
            }
            return buf.toByteArray()
        }
        // -- XML parsing ---------------------------------------------------------
        private fun parseListResponse(xml: String): List<MetaWebDavBackupFile> {
            if (xml.isBlank()) return emptyList()
            val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
                setInput(xml.reader())
            }
            val result = mutableListOf<MetaWebDavBackupFile>()
            var currentHref: String? = null
            var currentLastModified: Long = 0L
            var currentIsDir = false
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name.lowercase(Locale.ROOT)) {
                            "href" -> currentHref = parser.nextText()
                            "getlastmodified" -> {
                                val raw = parser.nextText()
                                currentLastModified = parseHttpDate(raw)
                            }
                            "collection" -> currentIsDir = true
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.lowercase(Locale.ROOT) == "response") {
                            val href = currentHref
                            if (!href.isNullOrBlank() && !currentIsDir) {
                                val name = extractFileName(href)
                                if (name.isNotBlank()) {
                                    result += MetaWebDavBackupFile(name = name, lastModified = currentLastModified)
                                }
                            }
                            currentHref = null
                            currentLastModified = 0L
                            currentIsDir = false
                        }
                    }
                }
                parser.next()
            }
            return result
        }
        private fun extractFileName(href: String): String {
            val decoded = URLDecoder.decode(href, Charsets.UTF_8.name())
            return decoded.trimEnd('/').substringAfterLast('/', "")
        }
        private fun parseHttpDate(raw: String?): Long {
            if (raw.isNullOrBlank()) return 0L
            return runCatching {
                ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
            }.getOrDefault(0L)
        }
    }
}
