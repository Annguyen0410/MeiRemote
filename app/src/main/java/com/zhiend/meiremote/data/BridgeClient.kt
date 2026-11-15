package com.zhiend.meiremote.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * HTTP client for the desktop browser's mobile bridge.
 *
 * Everything here mirrors the contract the desktop actually serves
 * (`litebrowser/services/android_bridge_service.py`, API_VERSION 1):
 * a bearer token on every call, `X-Mei-Timestamp` + `X-Mei-Nonce` on every
 * write, and the same action/result shapes the bridge returns. Everything is
 * UTF-8 end-to-end so Vietnamese / accented text, emoji and non-ASCII filenames
 * survive the trip unchanged.
 */
object BridgeClient {

    const val TIMEOUT_MS = 9000L

    /** android_bridge_service.API_VERSION — the contract this client speaks. */
    const val PROTOCOL_VERSION = 1

    /** android_bridge_service.MAX_UPLOAD_BYTES, in megabytes. */
    const val MAX_UPLOAD_MB = 25

    // Optional application-layer replay guard the desktop records.
    const val REPLAY_HEADER_TIMESTAMP = "X-Mei-Timestamp"
    const val REPLAY_HEADER_NONCE = "X-Mei-Nonce"
    const val REPLAY_WINDOW_SECONDS = 300

    const val OFFLINE_MESSAGE = "Desktop offline — đã lưu vào hàng chờ gửi sau"

    private const val BASE_PATH = "/api/mobile"

    data class Config(val host: String, val port: Int, val token: String) {
        fun baseUrl(): String = "http://$host:$port"
    }

    data class Result(
        val ok: Boolean,
        val message: String,
        val body: JSONObject? = null,
        val status: Int = 0,
        /** True only when the desktop could not be reached at all (not a refusal). */
        val connectionFailure: Boolean = false,
    )

    /** What the desktop says about itself: `GET /api/mobile/ping`. */
    data class Ping(
        val app: String,
        val bridge: String,
        val version: String,
        val protocolVersion: Int,
        val mode: String,
        val capabilities: Set<String>,
    ) {
        val modeLabel: String get() = if (mode == "lan") "LAN" else "chỉ máy này"

        fun supports(action: String): Boolean = capabilities.contains(action)

        companion object {
            fun from(body: JSONObject?): Ping? {
                if (body == null || !body.optBoolean("ok", false)) return null
                val caps = body.optJSONArray("capabilities")
                val actions = LinkedHashSet<String>()
                if (caps != null) {
                    for (i in 0 until caps.length()) {
                        val name = caps.optString(i)
                        if (name.isNotBlank()) actions.add(name)
                    }
                }
                return Ping(
                    app = body.optString("app", "Mei"),
                    bridge = body.optString("bridge", "litebrowser-mobile"),
                    version = body.optString("version", ""),
                    protocolVersion = body.optInt("protocol_version", 0),
                    mode = body.optString("mode", ""),
                    capabilities = actions,
                )
            }
        }
    }

    // ----- reads -------------------------------------------------------------

    fun ping(cfg: Config): Result {
        val res = call(cfg, "$BASE_PATH/ping", "GET")
        return if (res.ok) res.copy(message = "Desktop online") else res
    }

    /** `GET /api/mobile/capabilities`: actions, limits, replay header names. */
    fun capabilities(cfg: Config): Result = call(cfg, "$BASE_PATH/capabilities", "GET")

    /** `GET /api/mobile/today[?day=YYYY-MM-DD]`: read-only desk snapshot. */
    fun today(cfg: Config, day: String = ""): Result {
        val path = if (DAY_RE.matches(day.trim())) "$BASE_PATH/today?day=${day.trim()}" else "$BASE_PATH/today"
        return call(cfg, path, "GET")
    }

    // ----- writes ------------------------------------------------------------

    fun ingest(cfg: Config, action: String, payload: JSONObject): Result {
        val envelope = JSONObject()
            .put("action", action)
            .put("source", "android.mei_remote")
            .put("timestamp", nowIso())
            .put("payload", payload)
        return call(
            cfg,
            "$BASE_PATH/ingest",
            "POST",
            body = envelope.toString().toByteArray(StandardCharsets.UTF_8),
            contentType = "application/json; charset=utf-8",
        )
    }

    /** Ask the desktop Mei to open a chain web app (or any URL) in a new tab. */
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
        val boundary = "----MeiBoundary" + UUID.randomUUID().toString().replace("-", "")
        val body = buildMultipart(
            boundary = boundary,
            fields = mapOf("relative_target" to relativeTarget, "caption" to caption),
            fileField = "file",
            filename = filename ?: file.name,
            mime = mime,
            bytes = file.readBytes(),
        )
        return call(
            cfg,
            "$BASE_PATH/upload",
            "POST",
            body = body,
            contentType = "multipart/form-data; boundary=$boundary",
        )
    }

    // ----- internals ---------------------------------------------------------

    private fun call(
        cfg: Config,
        path: String,
        method: String,
        body: ByteArray? = null,
        contentType: String? = null,
    ): Result {
        return try {
            val conn = open(cfg.baseUrl() + path, method, cfg)
            try {
                if (body != null) {
                    conn.doOutput = true
                    if (contentType != null) conn.setRequestProperty("Content-Type", contentType)
                    conn.setFixedLengthStreamingMode(body.size)
                    conn.outputStream.use { it.write(body) }
                }
                val code = conn.responseCode
                val json = parseObject(readBody(if (code in 200..299) conn.inputStream else conn.errorStream))
                if (code in 200..299 && json?.optBoolean("ok", false) == true) {
                    Result(true, successMessage(json), json, code)
                } else {
                    Result(false, statusMessage(code, json), json, code)
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result(false, OFFLINE_MESSAGE, null, 0, connectionFailure = true)
        }
    }

    private fun open(url: String, method: String, cfg: Config): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = TIMEOUT_MS.toInt()
        conn.readTimeout = TIMEOUT_MS.toInt()
        conn.useCaches = false
        conn.setRequestProperty("Authorization", "Bearer ${cfg.token}")
        conn.setRequestProperty("Accept", "application/json; charset=utf-8")
        if (method == "POST") {
            // Epoch seconds, not millis, and a nonce inside the desktop's
            // alphabet: the bridge rejects a malformed pair with 400 rather than
            // quietly ignoring it.
            conn.setRequestProperty(REPLAY_HEADER_TIMESTAMP, (System.currentTimeMillis() / 1000).toString())
            conn.setRequestProperty(REPLAY_HEADER_NONCE, UUID.randomUUID().toString())
        }
        return conn
    }

    private fun successMessage(json: JSONObject): String {
        val result = json.optJSONObject("result") ?: JSONObject()
        return when (json.optString("action")) {
            "create_note" -> result.optString("note_id").takeIf { it.isNotBlank() }?.let { "Đã lưu: $it" }
                ?: "Đã lưu ghi chú"
            "append_to_library" -> result.optString("saved_page_id").takeIf { it.isNotBlank() }?.let { "Đã lưu trang: $it" }
                ?: result.optString("note_id").takeIf { it.isNotBlank() }?.let { "Đã lưu: $it" }
                ?: "Đã lưu vào Library"
            "save_page" -> "Đã lưu trang vào Library"
            "create_task" -> "Đã tạo nhiệm vụ" + result.optString("bucket").takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            "create_drawing" -> "Đã lưu bản vẽ"
            "import_tabs_batch" -> {
                val count = if (result.has("tab_count")) result.optInt("tab_count") else 0
                if (count > 0) "Đã gửi $count tab vào Import Center" else "Đã gửi tab vào Import Center"
            }
            "open_app" -> result.optString("label").takeIf { it.isNotBlank() }?.let { "Máy tính đang mở $it" }
                ?: "Máy tính đang mở app"
            "upload_file" -> result.optString("path").takeIf { it.isNotBlank() }?.let { "Đã lưu: $it" }
                ?: "Đã lưu tệp"
            else -> "Đã gửi lên desktop"
        }
    }

    /** Turn a refusal into a sentence a person can act on. */
    private fun statusMessage(code: Int, json: JSONObject?): String {
        val server = json?.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
        return when (code) {
            400 -> server ?: "Máy tính không hiểu yêu cầu này"
            401 -> "Token sai hoặc thiếu — ghép nối lại bằng mã MEI1 trong Cài đặt"
            403 -> "Máy tính từ chối IP này — bật “Listen on LAN” hoặc dùng IP nội bộ"
            404 -> "Bridge không có endpoint này — cập nhật Mei trên máy tính"
            409 -> server ?: "Máy tính coi yêu cầu này là trùng hoặc quá cũ — thử lại"
            413 -> "Tệp quá lớn (tối đa $MAX_UPLOAD_MB MB)"
            415 -> "Sai kiểu nội dung khi tải tệp"
            429 -> "Quá nhiều yêu cầu — chờ một phút rồi thử lại"
            500, 502, 503 -> server ?: "Máy tính gặp lỗi khi xử lý — thử lại"
            else -> server ?: "Máy tính trả về lỗi $code"
        }
    }

    private fun parseObject(raw: String): JSONObject? {
        if (raw.isBlank()) return null
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            null
        }
    }

    private fun readBody(stream: java.io.InputStream?): String {
        if (stream == null) return ""
        return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
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

    /** android_bridge_service.DAY_PARAM_RE — a day the desk will actually read. */
    private val DAY_RE = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
}
