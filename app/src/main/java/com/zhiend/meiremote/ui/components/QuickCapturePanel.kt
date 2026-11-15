package com.zhiend.meiremote.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zhiend.meiremote.data.AppState
import com.zhiend.meiremote.data.VoiceInput
import com.zhiend.meiremote.ui.theme.LocalMeiColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val MODES = listOf("Ghi chú", "Nhiệm vụ", "Liên kết")
private val SECTIONS = listOf("Personal", "Library", "Khác")
private val BUCKETS = listOf("Inbox", "Công việc", "Cá nhân", "Học tập", "Khác")
private val DUES = listOf("Không hạn", "Hôm nay", "Mai", "Tuần sau")

/**
 * The complete quick-capture UI (note / task / links with voice input).
 * Shared by the Notes tab and the floating bubble so both stay identical.
 */
@Composable
fun QuickCapturePanel(state: AppState) {
    var mode by rememberSaveable { mutableStateOf("Ghi chú") }
    var message by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MeiSectionCard(
            title = "Loại nội dung",
            subtitle = "Chọn thứ bạn muốn gửi lên MeiBrowser",
        ) {
            MeiChipRow(MODES, mode) { mode = it }
        }

        when (mode) {
            "Nhiệm vụ" -> TaskSection(state, scope, onMessage = { message = it })
            "Liên kết" -> LinkSection(state, scope, onMessage = { message = it })
            else -> NoteSection(state, scope, onMessage = { message = it })
        }

        MeiMessageBanner(message)
        MeiMessageBanner(state.lastMessage.takeIf { it.isNotBlank() && message.isBlank() })
    }
}

// ---------------- Ghi chú ----------------

@Composable
private fun NoteSection(state: AppState, scope: CoroutineScope, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    var section by rememberSaveable { mutableStateOf(if (state.prefs.defaultCategory in SECTIONS) state.prefs.defaultCategory else "Personal") }
    var customCategory by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }

    val category = when (section) {
        "Khác" -> customCategory.trim().ifBlank { "Mobile/Captures" }
        else -> section
    }

    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
                sr.setRecognitionListener(object : android.speech.RecognitionListener {
                    override fun onResults(results: android.os.Bundle?) {
                        listening = false
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (!text.isNullOrBlank()) {
                            body = if (body.isBlank()) text else "$body\n$text"
                        } else {
                            onMessage("Không nghe rõ, thử lại")
                        }
                    }

                    override fun onError(error: Int) {
                        listening = false
                        onMessage(VoiceInput.errorMessage(error))
                    }

                    override fun onReadyForSpeech(params: android.os.Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: android.os.Bundle?) {}
                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                })
            }
        } else {
            null
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            try {
                recognizer?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val sr = recognizer
            if (sr != null) {
                listening = true
                VoiceInput.start(sr)
            } else {
                onMessage("Thiết bị không hỗ trợ nhận dạng giọng nói")
            }
        } else {
            onMessage("Cần quyền micro để ghi giọng nói")
        }
    }

    fun toggleMic() {
        if (recognizer == null) {
            onMessage("Thiết bị không hỗ trợ nhận dạng giọng nói")
            return
        }
        if (listening) {
            listening = false
            VoiceInput.stop(recognizer)
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            listening = true
            VoiceInput.start(recognizer)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    MeiSectionCard(
        title = "Ghi chú",
        subtitle = "Mục Personal / Library — hỗ trợ tiếng Việt, emoji (UTF-8)",
    ) {
        MeiChipRow(SECTIONS, section) { section = it }
        if (section == "Khác") {
            Spacer(Modifier.height(8.dp))
            MeiTextField(customCategory, { customCategory = it }, "Danh mục tùy chỉnh", "vd: Du lịch / Ý tưởng")
        }
        Spacer(Modifier.height(8.dp))
        MeiTextField(title, { title = it }, "Tiêu đề", "Tiêu đề ghi chú")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MeiFieldLabel("Nội dung")
            Spacer(Modifier.weight(1f))
            MicButton(listening) { toggleMic() }
        }
        MeiTextField(
            body,
            { body = it },
            "Nội dung",
            "Viết ghi chú hoặc bấm 🎤 để nói…",
            minLines = 5,
            singleLine = false,
        )
        Spacer(Modifier.height(8.dp))
        MeiTextField(tags, { tags = it }, "Thẻ (phân cách bằng dấu phẩy)", "vd: ý tưởng, công việc")
    }

    MeiPrimaryButton(
        text = if (state.sending) "Đang gửi…" else "Gửi lên desktop",
        loading = state.sending,
        enabled = title.isNotBlank() && (body.isNotBlank() || tags.isNotBlank()) && !state.sending,
    ) {
        scope.launch {
            val tagList = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val msg = state.sendNote(category, title.trim(), body.trim(), tagList)
            onMessage(msg)
            if (msg.startsWith("Đã lưu") || msg.startsWith("Desktop offline")) {
                title = ""
                body = ""
                tags = ""
            }
        }
    }
}

// ---------------- Nhiệm vụ ----------------

@Composable
private fun TaskSection(state: AppState, scope: CoroutineScope, onMessage: (String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var bucket by rememberSaveable { mutableStateOf("Inbox") }
    var customBucket by rememberSaveable { mutableStateOf("") }
    var due by rememberSaveable { mutableStateOf("Không hạn") }
    var notes by rememberSaveable { mutableStateOf("") }

    val dueIso = when (due) {
        "Hôm nay" -> isoDate(0)
        "Mai" -> isoDate(1)
        "Tuần sau" -> isoDate(7)
        else -> ""
    }

    MeiSectionCard(
        title = "Nhiệm vụ",
        subtitle = "Tạo task trên desktop (Personal Hub → Tasks)",
    ) {
        MeiTextField(title, { title = it }, "Tiêu đề nhiệm vụ", "vd: Soạn báo cáo tuần")
        Spacer(Modifier.height(8.dp))
        MeiFieldLabel("Nhóm (bucket)")
        MeiChipRow(BUCKETS, bucket) { bucket = it }
        if (bucket == "Khác") {
            Spacer(Modifier.height(8.dp))
            MeiTextField(customBucket, { customBucket = it }, "Nhóm tùy chỉnh", "vd: Dự án X")
        }
        Spacer(Modifier.height(8.dp))
        MeiFieldLabel("Hạn chót")
        MeiChipRow(DUES, due) { due = it }
        Spacer(Modifier.height(8.dp))
        MeiTextField(notes, { notes = it }, "Ghi chú kèm (tùy chọn)", "Chi tiết thêm…", minLines = 2, singleLine = false)
    }

    MeiPrimaryButton(
        text = if (state.sending) "Đang gửi…" else "Gửi nhiệm vụ",
        loading = state.sending,
        enabled = title.isNotBlank() && !state.sending,
    ) {
        scope.launch {
            val realBucket = if (bucket == "Khác") customBucket.trim().ifBlank { "Inbox" } else bucket
            val msg = state.sendTask(title.trim(), realBucket, dueIso, notes.trim())
            onMessage(msg)
            if (msg.startsWith("Đã lưu") || msg.startsWith("Desktop offline")) {
                title = ""
                notes = ""
            }
        }
    }
}

// ---------------- Liên kết / tab ----------------

@Composable
private fun LinkSection(state: AppState, scope: CoroutineScope, onMessage: (String) -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }
    var urlsText by rememberSaveable { mutableStateOf("") }

    val urls = urlsText.lines().map { it.trim() }.filter { it.isNotBlank() }
    val firstUrl = urls.firstOrNull { it.startsWith("http://") || it.startsWith("https://") } ?: urls.firstOrNull()

    MeiSectionCard(
        title = "Liên kết / tab",
        subtitle = "Gửi nhiều link thành tab batch vào Import Center, hoặc lưu một trang vào Library",
    ) {
        MeiTextField(label, { label = it }, "Nhãn bộ tab (tùy chọn)", "vd: Đọc tối nay")
        Spacer(Modifier.height(8.dp))
        MeiFieldLabel("Các đường dẫn (mỗi dòng một link)")
        MeiTextField(
            urlsText,
            { urlsText = it },
            "URLs",
            "https://example.com\nhttps://example.org",
            minLines = 4,
            singleLine = false,
        )
    }

    MeiPrimaryButton(
        text = if (state.sending) "Đang gửi…" else "Gửi tab batch (${urls.size} tab)",
        loading = state.sending,
        enabled = urls.isNotEmpty() && !state.sending,
    ) {
        scope.launch {
            val msg = state.sendTabsBatch(label.trim(), urls)
            onMessage(msg)
            if (msg.startsWith("Đã gửi") || msg.startsWith("Desktop offline")) {
                urlsText = ""
                label = ""
            }
        }
    }

    if (firstUrl != null) {
        Spacer(Modifier.height(8.dp))
        MeiPrimaryButton(
            text = "Lưu trang đầu vào Library",
            enabled = !state.sending,
        ) {
            scope.launch {
                val msg = state.savePage(firstUrl, label.trim())
                onMessage(msg)
            }
        }
    }
}

@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    val c = LocalMeiColors.current
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(if (listening) c.danger else c.accentSoft, CircleShape)
            .border(1.dp, if (listening) c.danger else c.inputBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (listening) "■" else "🎤", fontSize = 13.sp, color = if (listening) c.cardBg else c.text)
    }
}

private fun isoDate(daysFromNow: Int): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, daysFromNow)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
}
