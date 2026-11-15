package com.zhiend.meiremote.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

enum class ConnStatus { UNKNOWN, CHECKING, ONLINE, OFFLINE }

/**
 * Single source of truth for the app: connection state, immediate sends and the
 * offline → auto-upload flow. When a send cannot reach the desktop it is queued
 * in the outbox and flushed automatically the moment both machines are online.
 */
class AppState(context: Context) {

    val prefs = BridgePrefs(context)
    val outbox = Outbox(context)

    var status by mutableStateOf(ConnStatus.UNKNOWN)
        private set
    var statusText by mutableStateOf("Chưa kết nối")
        private set
    var lastMessage by mutableStateOf("")
        private set
    var outboxCount by mutableStateOf(outbox.pendingCount())
        private set
    var syncing by mutableStateOf(false)
        private set
    var sending by mutableStateOf(false)
        private set
    var themeMode by mutableStateOf(prefs.themeMode)
        private set

    /** What the desktop said it can do on the last successful ping. Empty = not asked yet. */
    var capabilities by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Read-only "what is on the desk today" snapshot, or null before the first read. */
    var desk by mutableStateOf<DeskToday?>(null)
        private set
    var deskLoading by mutableStateOf(false)
        private set

    /** True unless the desktop told us it cannot do this. An unasked bridge is not a refusal. */
    fun supports(action: String): Boolean = capabilities.isEmpty() || capabilities.contains(action)

    /** Content handed in from another app via a share intent (text/URL or image). */
    var sharedText by mutableStateOf<String?>(null)
        private set
    var sharedImageUri by mutableStateOf<String?>(null)
        private set

    fun applyThemeMode(mode: String) {
        prefs.themeMode = mode
        themeMode = mode
    }

    fun acceptShare(text: String?, imageUri: String?) {
        sharedText = text?.takeIf { it.isNotBlank() }
        sharedImageUri = imageUri
    }

    fun consumeShare() {
        sharedText = null
        sharedImageUri = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null

    fun config(): BridgeClient.Config = BridgeClient.Config(prefs.host, prefs.port, prefs.token)

    fun startAutoSync() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            while (true) {
                if (prefs.autoSync && prefs.isConfigured() && outboxCount > 0) {
                    flushOutbox()
                } else if (prefs.autoSync && prefs.isConfigured() && status != ConnStatus.ONLINE) {
                    // Keep the "online" pill fresh in the background.
                    testConnection(silent = true)
                }
                delay(20_000)
            }
        }
    }

    fun stopAutoSync() {
        syncJob?.cancel()
        syncJob = null
    }

    /** Ping the desktop and update connection state. Returns true when reachable. */
    suspend fun testConnection(silent: Boolean = false): Boolean {
        status = ConnStatus.CHECKING
        if (!silent) statusText = "Đang kiểm tra…"
        val cfg = config()
        if (!prefs.isConfigured()) {
            status = ConnStatus.OFFLINE
            statusText = "Chưa nhập host/port/token"
            return false
        }
        val res = BridgeClient.ping(cfg)
        if (res.ok) {
            status = ConnStatus.ONLINE
            val info = BridgeClient.Ping.from(res.body)
            if (info != null) {
                capabilities = info.capabilities
                prefs.bridgeVersion = info.version
                prefs.protocolVersion = info.protocolVersion
                prefs.bridgeMode = info.mode
                statusText = "Desktop online — ${cfg.host}:${cfg.port}" +
                    (if (info.version.isNotBlank()) " · ${info.version}" else "")
            } else {
                statusText = "Desktop online — ${cfg.host}:${cfg.port}"
            }
            prefs.lastPingOk = true
            prefs.lastPingAt = System.currentTimeMillis()
        } else {
            status = ConnStatus.OFFLINE
            statusText = "Desktop offline"
            prefs.lastPingOk = false
        }
        return res.ok
    }

    /**
     * Read today's desk from the desktop (GET /api/mobile/today). Read-only: the
     * bridge returns a trimmed snapshot and changes no local data, so this is
     * safe to tap while the outbox still has items.
     */
    suspend fun refreshToday(day: String = ""): String {
        if (!prefs.isConfigured()) return "Chưa nhập host/port/token — vào Cài đặt"
        deskLoading = true
        try {
            val res = BridgeClient.today(config(), day)
            if (!res.ok) {
                lastMessage = res.message
                return res.message
            }
            val snapshot = DeskToday.from(res.body)
            if (snapshot == null) {
                lastMessage = "Máy tính trả về dữ liệu không đọc được"
                return lastMessage
            }
            desk = snapshot
            lastMessage = "Đã đọc bảng điều khiển trên desktop"
            return lastMessage
        } finally {
            deskLoading = false
        }
    }

    /** Send a note; queues it when the desktop cannot be reached. */
    suspend fun sendNote(category: String, title: String, body: String, tags: List<String>): String {
        sending = true
        try {
            val payload = JSONObject()
                .put("category", category)
                .put("title", title)
                .put("body", body)
                .put("tags", org.json.JSONArray(tags))
            val res = BridgeClient.ingest(config(), "create_note", payload)
            if (res.ok) {
                lastMessage = res.message
                return res.message
            }
            if (res.connectionFailure) {
                outbox.addNote(category, title, body, tags)
                refreshOutbox()
                lastMessage = "Desktop offline — đã lưu vào hàng chờ ($outboxCount mục)"
                return lastMessage
            }
            lastMessage = res.message
            return res.message
        } finally {
            sending = false
        }
    }

    /** Send a file; queues it when offline. */
    suspend fun sendFile(file: File, name: String, target: String, caption: String, mime: String): String {
        sending = true
        try {
            val res = BridgeClient.uploadFile(config(), file, target, caption, mime, filename = name)
            if (res.ok) {
                lastMessage = res.message
                return res.message
            }
            if (res.connectionFailure) {
                outbox.addFile(file, name, target, caption, mime)
                refreshOutbox()
                lastMessage = "Desktop offline — đã lưu vào hàng chờ ($outboxCount mục)"
                return lastMessage
            }
            lastMessage = res.message
            return res.message
        } finally {
            sending = false
        }
    }

    /** Send a drawing (PNG) as a note + attachment; queues it when offline. */
    suspend fun sendDrawing(title: String, category: String, body: String, pngBytes: ByteArray): String {
        sending = true
        try {
            val payload = JSONObject()
                .put("title", title)
                .put("category", category)
                .put("body", body)
                .put("image_base64", Outbox.encodeBase64(pngBytes))
            val res = BridgeClient.ingest(config(), "create_drawing", payload)
            if (res.ok) {
                lastMessage = res.message
                return res.message
            }
            if (res.connectionFailure) {
                outbox.addDrawing(title, category, body, pngBytes)
                refreshOutbox()
                lastMessage = "Desktop offline — đã lưu vào hàng chờ ($outboxCount mục)"
                return lastMessage
            }
            lastMessage = res.message
            return res.message
        } finally {
            sending = false
        }
    }

    /** Create a task on the desktop; queues it when offline. */
    suspend fun sendTask(title: String, bucket: String, dueAt: String, notes: String): String {
        sending = true
        try {
            val payload = JSONObject()
                .put("title", title)
                .put("bucket", bucket.ifBlank { "Inbox" })
                .put("due_at", dueAt)
                .put("notes", notes)
            val res = BridgeClient.ingest(config(), "create_task", payload)
            if (res.ok) {
                lastMessage = res.message
                return res.message
            }
            if (res.connectionFailure) {
                outbox.addTask(title, bucket.ifBlank { "Inbox" }, dueAt, notes)
                refreshOutbox()
                lastMessage = "Desktop offline — đã lưu vào hàng chờ ($outboxCount mục)"
                return lastMessage
            }
            lastMessage = res.message
            return res.message
        } finally {
            sending = false
        }
    }

    /** Send a set of links as a tab batch into the browser's Import Center. */
    suspend fun sendTabsBatch(label: String, urls: List<String>): String {
        val clean = urls.map { it.trim() }.filter { it.isNotBlank() }
        if (clean.isEmpty()) return "Chưa có đường dẫn nào"
        sending = true
        try {
            val tabs = org.json.JSONArray()
            clean.forEach { url ->
                tabs.put(JSONObject().put("url", url).put("title", url).put("active", false).put("pinned", false))
            }
            val payload = JSONObject()
                .put("source_browser", "android_remote")
                .put("source_label", label.ifBlank { "Từ điện thoại" })
                .put("tabs", tabs)
            val res = BridgeClient.ingest(config(), "import_tabs_batch", payload)
            if (res.ok) {
                lastMessage = "Đã gửi ${clean.size} tab vào Import Center"
                return lastMessage
            }
            if (res.connectionFailure) {
                outbox.addTabs(label.ifBlank { "Từ điện thoại" }, clean)
                refreshOutbox()
                lastMessage = "Desktop offline — đã lưu vào hàng chờ ($outboxCount mục)"
                return lastMessage
            }
            lastMessage = res.message
            return res.message
        } finally {
            sending = false
        }
    }

    /** Yêu cầu MeiBrowser trên máy tính mở một web app / URL trong tab mới. */
    suspend fun openOnDesktop(appId: String, url: String, label: String = ""): String {
        if (!prefs.isConfigured()) return "Chưa nhập host/port/token — vào Cài đặt"
        if (!supports("open_app")) return "Bản Mei trên máy tính chưa hỗ trợ mở app từ xa — cập nhật Mei rồi thử lại"
        sending = true
        try {
            val res = BridgeClient.openApp(config(), appId, url, label)
            if (res.ok) {
                lastMessage = "Đã gửi máy tính mở ${label.ifBlank { appId }}"
                return lastMessage
            }
            if (res.connectionFailure) return "Desktop offline — bật MeiBrowser (bridge) rồi thử lại"
            lastMessage = res.message
            return res.message
        } finally {
            sending = false
        }
    }

    /** Save a page to the desktop Library; queues it when offline. */
    suspend fun savePage(url: String, title: String): String {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return "Chưa có đường dẫn"
        sending = true
        try {
            val payload = JSONObject()
                .put("url", cleanUrl)
                .put("title", title.trim().ifBlank { cleanUrl })
                .put("source_app", "android.mei_remote")
            val res = BridgeClient.ingest(config(), "save_page", payload)
            if (res.ok) {
                lastMessage = "Đã lưu trang vào Library"
                return lastMessage
            }
            if (res.connectionFailure) {
                outbox.addPage(cleanUrl, title.trim())
                refreshOutbox()
                lastMessage = "Desktop offline — đã lưu vào hàng chờ ($outboxCount mục)"
                return lastMessage
            }
            lastMessage = res.message
            return res.message
        } finally {
            sending = false
        }
    }

    /**
     * Smart one-line capture: URL → save page, "/task ..." → task, else → note.
     * Returns the result message.
     */
    suspend fun quickCapture(text: String): String {
        val t = text.trim()
        if (t.isBlank()) return "Chưa có nội dung"
        val url = extractUrl(t)
        if (url != null) {
            val title = t.replace(url, "").trim().ifBlank { url }
            return savePage(url, title)
        }
        if (t.startsWith("/task")) {
            return sendTask(t.removePrefix("/task").trim().ifBlank { "Nhiệm vụ mới" }, "Inbox", "", "")
        }
        return sendNote(prefs.defaultCategory, t.take(60), t, emptyList())
    }

    /** Push every queued item to the desktop. Stops at the first connection failure. */
    suspend fun flushOutbox(): String {
        val pending = outbox.pending()
        if (pending.isEmpty()) {
            lastMessage = "Hàng chờ trống"
            return lastMessage
        }
        if (!testConnection(silent = true)) {
            lastMessage = "Desktop offline — chưa gửi được ($outboxCount mục)"
            return lastMessage
        }
        syncing = true
        try {
            val cfg = config()
            var sent = 0
            for (entry in pending) {
                val id = entry.optString("id")
                val kind = entry.optString("kind")
                val res = when (kind) {
                    "note" -> BridgeClient.ingest(
                        cfg,
                        "create_note",
                        JSONObject()
                            .put("category", entry.optString("category"))
                            .put("title", entry.optString("title"))
                            .put("body", entry.optString("body"))
                            .put("tags", entry.optJSONArray("tags") ?: org.json.JSONArray()),
                    )
                    "file" -> {
                        val f = File(entry.optString("file"))
                        if (f.exists()) {
                            BridgeClient.uploadFile(
                                cfg,
                                f,
                                entry.optString("relative_target"),
                                entry.optString("caption"),
                                entry.optString("mime"),
                                filename = entry.optString("filename"),
                            )
                        } else {
                            BridgeClient.Result(false, "Mất tệp")
                        }
                    }
                    "drawing" -> {
                        val f = File(entry.optString("file"))
                        if (f.exists()) {
                            BridgeClient.ingest(
                                cfg,
                                "create_drawing",
                                JSONObject()
                                    .put("title", entry.optString("title"))
                                    .put("category", entry.optString("category"))
                                    .put("body", entry.optString("body"))
                                    .put("image_base64", Outbox.encodeBase64(f.readBytes())),
                            )
                        } else {
                            BridgeClient.Result(false, "Mất bản vẽ")
                        }
                    }
                    "task" -> BridgeClient.ingest(
                        cfg,
                        "create_task",
                        JSONObject()
                            .put("title", entry.optString("title"))
                            .put("bucket", entry.optString("bucket").ifBlank { "Inbox" })
                            .put("due_at", entry.optString("due_at"))
                            .put("notes", entry.optString("notes")),
                    )
                    "tabs" -> {
                        val urls = entry.optJSONArray("urls")
                        val tabs = org.json.JSONArray()
                        if (urls != null) {
                            for (i in 0 until urls.length()) {
                                val u = urls.optString(i)
                                if (u.isNotBlank()) {
                                    tabs.put(JSONObject().put("url", u).put("title", u).put("active", false).put("pinned", false))
                                }
                            }
                        }
                        if (tabs.length() == 0) {
                            BridgeClient.Result(false, "Không có tab")
                        } else {
                            BridgeClient.ingest(
                                cfg,
                                "import_tabs_batch",
                                JSONObject()
                                    .put("source_browser", "android_remote")
                                    .put("source_label", entry.optString("label").ifBlank { "Từ điện thoại" })
                                    .put("tabs", tabs),
                            )
                        }
                    }
                    "page" -> BridgeClient.ingest(
                        cfg,
                        "save_page",
                        JSONObject()
                            .put("url", entry.optString("url"))
                            .put("title", entry.optString("title").ifBlank { entry.optString("url") })
                            .put("source_app", "android.mei_remote"),
                    )
                    else -> BridgeClient.Result(false, "Loại không hợp lệ")
                }
                if (res.ok) {
                    outbox.remove(id)
                    sent++
                } else if (res.connectionFailure) {
                    break
                }
            }
            refreshOutbox()
            lastMessage = if (sent > 0) "Đã đồng bộ $sent mục lên desktop" else "Không gửi được mục nào"
            return lastMessage
        } finally {
            syncing = false
        }
    }

    fun refreshOutbox() {
        outboxCount = outbox.pendingCount()
    }

    fun clearOutbox() {
        outbox.clear()
        refreshOutbox()
    }
}
