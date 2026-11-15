package com.zhiend.meiremote.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiend.meiremote.data.AppState
import com.zhiend.meiremote.data.ChainApp
import com.zhiend.meiremote.data.ChainApps
import com.zhiend.meiremote.ui.components.MeiFieldLabel
import com.zhiend.meiremote.ui.components.MeiMessageBanner
import com.zhiend.meiremote.ui.components.MeiPageHeader
import com.zhiend.meiremote.ui.components.MeiTextField
import com.zhiend.meiremote.ui.theme.LocalMeiColors
import kotlinx.coroutines.launch
import java.net.URLEncoder

/** Mô tả form nhập nhanh của từng web app (bê điều kiện từ web xuống). */
private data class QuickField(val key: String, val label: String, val placeholder: String, val required: Boolean = false)

private data class QuickForm(val appId: String, val title: String, val hint: String, val fields: List<QuickField>)

private val QUICK_FORMS: Map<String, QuickForm> = mapOf(
    "boitoan" to QuickForm(
        "boitoan",
        "Hỏi Bói Toán Web",
        "Nhập câu hỏi + điều kiện y như trên web — web sẽ tự gieo quẻ và trả lời.",
        listOf(
            QuickField("q", "Câu hỏi bói *", "Hãy đặt câu hỏi với tâm thành kính…", required = true),
            QuickField("name", "Tên người xem", "VD: Nguyễn Văn A"),
            QuickField("birth", "Ngày sinh", "VD: 15/03/1995 (dd/mm/yyyy)"),
        ),
    ),
    "mas" to QuickForm(
        "mas",
        "MAS — Bắt đầu huấn luyện",
        "Tạo sẵn session trên MAS với mục tiêu của bạn (backend chạy Render).",
        listOf(
            QuickField("target", "Mục tiêu *", "VD: pencil, guitar, stairs, audience…", required = true),
            QuickField("domain", "Lĩnh vực", "VD: object, body, mind, social"),
            QuickField("goal", "Mục đích", "Bạn muốn đạt được điều gì?"),
        ),
    ),
    "linklumina" to QuickForm(
        "linklumina",
        "Lưu link vào LinkLumina",
        "Gửi link / note / prompt thẳng lên LinkLumina — app tự lưu bằng đúng cơ chế lưu trữ của nó.",
        listOf(
            QuickField("add", "Đường dẫn cần lưu *", "https://…", required = true),
            QuickField("title", "Tiêu đề", "Tên hiển thị của link"),
            QuickField("note", "Ghi chú", "Prompt, ghi chú kèm theo link…"),
        ),
    ),
)

private fun buildQuickUrl(app: ChainApp, values: Map<String, String>): String {
    val sb = StringBuilder(app.remote)
    sb.append("?")
    var first = true
    values.forEach { (key, value) ->
        if (value.isNotBlank()) {
            if (!first) sb.append("&")
            first = false
            sb.append(key).append("=").append(URLEncoder.encode(value, "UTF-8"))
        }
    }
    return sb.toString()
}

@Composable
fun WebAppsScreen(
    state: AppState,
    onOpenInApp: (ChainApp, String) -> Unit,
) {
    val c = LocalMeiColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var quickAppId by rememberSaveable { mutableStateOf("") }
    var fieldValues by remember { mutableStateOf(mapOf<String, String>()) }
    var message by rememberSaveable { mutableStateOf("") }

    val quickApp = quickAppId.takeIf { it.isNotBlank() }?.let { ChainApps.byId(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MeiPageHeader(
            "Web Apps — Project Chain",
            "6 web tỉnh + hub, tất cả liên kết với MeiBrowser. Mở ngay trên điện thoại, hoặc điều khiển máy tính mở bản local.",
        )

        MeiMessageBanner(message)

        ChainApps.apps.forEach { app ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.cardBg, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(app.color).copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.glyph, fontSize = 24.sp)
                    Spacer(Modifier.size(10.dp))
                    Column {
                        Text(app.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(app.subtitle, color = c.textMuted, fontSize = 11.sp)
                    }
                }
                if (app.remote.isBlank()) {
                    // App local-only (Project Hub): không có URL online để mở trên điện thoại
                    Text(
                        "💻 Chỉ chạy local trên máy tính — bấm \"Máy tính\" để MeiBrowser mở bản local.",
                        color = c.textMuted,
                        fontSize = 11.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionChip("Máy tính", Modifier.weight(1f), accent = app.color) {
                            scope.launch {
                                message = state.openOnDesktop(app.id, app.remote, app.name)
                            }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionChip("▶ Mở trong app", Modifier.weight(1f), accent = app.color) {
                            onOpenInApp(app, app.remote)
                        }
                        ActionChip("Máy tính", Modifier.weight(1f), accent = app.color) {
                            scope.launch {
                                message = state.openOnDesktop(app.id, app.remote, app.name)
                            }
                        }
                        ActionChip("Ngoài", Modifier.weight(1f), accent = app.color) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(app.remote))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
                val form = QUICK_FORMS[app.id]
                if (form != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.mainBgAlt, RoundedCornerShape(10.dp))
                            .clickable { quickAppId = app.id; fieldValues = emptyMap() }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⚡ ${form.title}", color = c.accentHover, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("nhập nhanh →", color = c.textMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }

    // Dialog nhập nhanh — bê đúng các điều kiện của web
    val form = quickApp?.let { QUICK_FORMS[it.id] }
    if (form != null && quickApp != null) {
        AlertDialog(
            onDismissRequest = { quickAppId = "" },
            containerColor = c.cardBg,
            title = { Text(form.title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(form.hint, color = c.textMuted, fontSize = 11.sp)
                    form.fields.forEach { field ->
                        MeiFieldLabel(field.label)
                        MeiTextField(
                            value = fieldValues[field.key] ?: "",
                            onValueChange = { fieldValues = fieldValues + (field.key to it) },
                            label = field.label,
                            placeholder = field.placeholder,
                            minLines = if (field.key == "note" || field.key == "q") 3 else 1,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val missing = form.fields.firstOrNull { it.required && (fieldValues[it.key] ?: "").isBlank() }
                    if (missing != null) {
                        message = "Thiếu: ${missing.label}"
                    } else {
                        val url = buildQuickUrl(quickApp, fieldValues)
                        onOpenInApp(quickApp, url)
                        quickAppId = ""
                    }
                }) { Text("Gửi & mở", color = c.accentHover, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { quickAppId = "" }) { Text("Hủy", color = c.textMuted) }
            },
        )
    }
}

@Composable
private fun ActionChip(text: String, modifier: Modifier = Modifier, accent: Long, onClick: () -> Unit) {
    val c = LocalMeiColors.current
    Box(
        modifier = modifier
            .background(c.mainBgAlt, RoundedCornerShape(10.dp))
            .border(1.dp, c.borderSoft, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color(accent), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}