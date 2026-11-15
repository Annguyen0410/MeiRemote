package com.zhiend.meiremote.data

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Minimal HTTP client for the desktop browser's mobile bridge.
 * Everything is UTF-8 end-to-end so Vietnamese / accented text, emoji and
 * non-ASCII filenames survive the trip unchanged.
 */
object BridgeClient {

    const val TIMEOUT_MS = 9000L

    data class Config(val host: String, val port: Int, val token: String) {
        fun baseUrl(): String = "http://$host:$port"
    }

    data class Result(val ok: Boolean, val message: String, val body: JSONObject? = null) {
        val isConnectionError: Boolean
            get() = !ok && (message.startsWith("Không kết nối") || message.startsWith("Hết thời gian") || message.startsWith("Desktop offline"))
    }

    fun ping(cfg: Config): Result {
        return try {
            val conn = open("${cfg.baseUrl()}/api/mobile/ping", "GET", cfg)
            try {
                val code = conn.responseCode
                if (code == 200) {
                    val json = JSONObject(readBody(conn.inputStream))
                    Result(true, "Desktop online", json)
                } else {
                    Result(false, "Lỗi ${code}: ${readError(conn)}")
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result(false, "Không kết nối được — kiểm tra host/port và xem desktop có bật bridge không")
        }
    }

    fun ingest(cfg: Config, action: String, payload: JSONObject): Result {
        return try {
            val envelope = JSONObject()
                .put("action", action)
                .put("source", "android.mei_remote")
                .put("timestamp", nowIso())
                .put("payload", payload)
            val body = envelope.toString().toByteArray(StandardCharsets.UTF_8)
            val conn = open("${cfg.baseUrl()}/api/mobile/ingest", "POST", cfg)
            try {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
                val code = conn.responseCode
                val json = JSONObject(readBody(if (code in 200..299) conn.inputStream else conn.errorStream))
                val ok = json.optBoolean("ok", false)
                val msg = if (ok) {
                    json.optJSONObject("result")?.optString("note_id")?.let { "Đã lưu: $it" }
                        ?: "Đã gửi lên desktop"
                } else {
                    json.optJSONObject("error")?.optString("message") ?: "Desktop từ chối"
                }
                Result(ok, msg, json)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result(false, "Desktop offline — đã lưu vào hàng chờ gửi sau")
        }
    }

    /** Ask the desktop MeiBrowser to open a chain web app (or any URL) in a new tab. */
    fun openApp(cfg: Config, appId: String, url: String, label: String = ""): Result {
        return ingest(
            cfg,
            "open_app",
            JSONObject()
                .put("id", appId)
                .put("url", url)
                .put("label", label.ifBlank { appId }),
        )
    }

    fun uploadFile(
        cfg: Config,
        file: File,
        relativeTarget: String,
        caption: String,
        mime: String,
        filename: String? = null,
    ): Result {
        return try {
            val boundary = "----MeiBoundary" + UUID.randomUUID().toString().replace("-", "")
            val body = buildMultipart(
                boundary = boundary,
                fields = mapOf("relative_target" to relativeTarget, "caption" to caption),
                fileField = "file",
                filename = filename ?: file.name,
                mime = mime,
                bytes = file.readBytes(),
            )
            val conn = open("${cfg.baseUrl()}/api/mobile/upload", "POST", cfg)
            try {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
                val code = conn.responseCode
                val json = JSONObject(readBody(if (code in 200..299) conn.inputStream else conn.errorStream))
                val ok = json.optBoolean("ok", false)
                val msg = if (ok) {
                    "Đã lưu: ${json.optJSONObject("result")?.optString("path") ?: file.name}"
                } else {
                    json.optJSONObject("error")?.optString("message") ?: "Desktop từ chối"
                }
                Result(ok, msg, json)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result(false, "Desktop offline — đã lưu vào hàng chờ gửi sau")
        }
    }

    // ----- internals -----

    private fun open(url: String, method: String, cfg: Config): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = TIMEOUT_MS.toInt()
        conn.readTimeout = TIMEOUT_MS.toInt()
        conn.useCaches = false
        conn.setRequestProperty("Authorization", "Bearer ${cfg.token}")
        conn.setRequestProperty("Accept", "application/json; charset=utf-8")
        return conn
    }

    private fun readBody(stream: java.io.InputStream?): String {
        if (stream == null) return "{}"
        return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    private fun readError(conn: HttpURLConnection): String {
        return try {
            readBody(conn.errorStream).take(300)
        } catch (e: Exception) {
            conn.responseMessage ?: "unknown"
        }
    }

    private fun nowIso(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .format(java.util.Date())
    }

    private fun buildMultipart(
        boundary: String,
        fields: Map<String, String>,
        fileField: String,
        filename: String,
        mime: String,
        bytes: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val crlf = "\r\n"

        fun writeString(s: String) = out.write(s.toByteArray(StandardCharsets.UTF_8))

        for ((name, value) in fields) {
            writeString("--$boundary$crlf")
            writeString("Content-Disposition: form-data; name=\"$name\"$crlf$crlf")
            writeString(value)
            writeString(crlf)
        }

        writeString("--$boundary$crlf")
        // Sanitize the filename so it can never inject headers (" / CR / LF / controls).
        val safeFilename = filename
            .replace(Regex("[\"\r\n\u0000-\u001f]"), "-")
            .trim(' ', '.')
            .ifBlank { "upload" }
        // RFC 5987 UTF-8 filename for non-ASCII names; plain filename for ASCII.
        val ascii = safeFilename.all { it.code < 128 }
        if (ascii) {
            writeString("Content-Disposition: form-data; name=\"$fileField\"; filename=\"$safeFilename\"$crlf")
        } else {
            val encoded = URLEncoder.encode(safeFilename, "UTF-8").replace("+", "%20")
            writeString("Content-Disposition: form-data; name=\"$fileField\"; filename*=UTF-8''$encoded$crlf")
        }
        writeString("Content-Type: $mime$crlf$crlf")
        out.write(bytes)
        writeString(crlf)
        writeString("--$boundary--$crlf")
        return out.toByteArray()
    }
}
