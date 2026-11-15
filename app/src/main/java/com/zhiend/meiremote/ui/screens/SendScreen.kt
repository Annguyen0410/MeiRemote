package com.zhiend.meiremote.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiend.meiremote.data.AppState
import com.zhiend.meiremote.ui.components.MeiChipRow
import com.zhiend.meiremote.ui.components.MeiFieldLabel
import com.zhiend.meiremote.ui.components.MeiMessageBanner
import com.zhiend.meiremote.ui.components.MeiPageHeader
import com.zhiend.meiremote.ui.components.MeiPrimaryButton
import com.zhiend.meiremote.ui.components.MeiSectionCard
import com.zhiend.meiremote.ui.components.MeiTextField
import com.zhiend.meiremote.ui.theme.LocalMeiColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val TARGETS = listOf("Photos", "Documents", "Inbox", "Drawings")

/**
 * In-memory thumbnail cache so picking the same image repeatedly is instant
 * instead of re-decoding a full bitmap off disk every time. Bounded to ~64
 * previews (a small thumbnail is a few KB) and cleared never — the app process
 * lifetime is the natural scope for a send picker.
 */
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    return "%.1f MB".format(kb / 1024)
}

private val previewCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 16).toInt()) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
}

private fun decodePreview(path: String, mime: String): Bitmap? {
    if (!mime.startsWith("image/")) return null
    previewCache.get(path)?.let { return it }
    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
    val bmp = try {
        BitmapFactory.decodeFile(path, opts)
    } catch (_: Exception) {
        null
    }
    if (bmp != null) {
        // inSampleSize=4 already caps dimensions; scale down hard if a decoder
        // ignores it, so the cache never holds a giant bitmap.
        val scale = if (bmp.width <= 512) bmp else Bitmap.createScaledBitmap(bmp, 512, (512f * bmp.height / bmp.width).toInt().coerceAtLeast(1), true)
        val stored = if (scale !== bmp) { bmp.recycle(); scale } else bmp
        previewCache.put(path, stored)
        return stored
    }
    return null
}

private data class PickedFile(
    val file: File,
    val name: String,
    val mime: String,
    val preview: Bitmap?,
)

@Composable
fun SendScreen(state: AppState) {
    val c = LocalMeiColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var picked by remember { mutableStateOf<List<PickedFile>>(emptyList()) }
    var reading by remember { mutableStateOf(false) }
    var caption by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("Photos") }
    var message by rememberSaveable { mutableStateOf("") }

    fun displayName(uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) ?: "" else ""
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun acceptUris(uris: List<Uri>) {
        if (uris.isEmpty() || reading) return
        // Copy + thumbnail off the main thread so large files never freeze the UI.
        scope.launch {
            reading = true
            val dir = File(context.cacheDir, "mei_pick").apply { mkdirs() }
            val added = mutableListOf<PickedFile>()
            uris.forEach { uri ->
                try {
                    val item = withContext(Dispatchers.IO) {
                        val name = displayName(uri).ifBlank { "file-${System.currentTimeMillis()}" }
                        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                        val tmp = File(dir, name)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            tmp.outputStream().use { output -> input.copyTo(output) }
                        }
                        val preview = decodePreview(tmp.absolutePath, mime)
                        PickedFile(tmp, name, mime, preview)
                    }
                    added.add(item)
                } catch (_: Exception) {
                    message = "Không đọc được một tệp đã chọn"
                }
            }
            picked = picked + added
            reading = false
        }
    }

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        acceptUris(uris)
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        acceptUris(uris)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MeiPageHeader("Gửi file / ảnh", "Chọn nhiều ảnh hay tài liệu để lưu vào SafeVault trên máy tính")

        MeiSectionCard(
            title = "Chọn tệp",
            subtitle = "Chọn nhiều ảnh cùng lúc, hoặc nhiều tệp bất kỳ trên điện thoại",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionBox("🖼", "Chọn nhiều ảnh", Modifier.weight(1f)) {
                    pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                ActionBox("📄", "Chọn nhiều file", Modifier.weight(1f)) {
                    pickFiles.launch(arrayOf("*/*"))
                }
            }
            if (reading) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Đang đọc tệp…",
                    color = c.accentHover,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            } else if (picked.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                val totalBytes = picked.sumOf { it.file.length() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Đã chọn ${picked.size} tệp · ${formatBytes(totalBytes)}",
                        color = c.textMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Xóa tất cả",
                        color = c.danger,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { picked = emptyList() }.padding(6.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    picked.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(c.mainBgAlt, RoundedCornerShape(10.dp))
                                .border(1.dp, c.borderSoft, RoundedCornerShape(10.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (item.preview != null) {
                                Image(
                                    bitmap = item.preview!!.asImageBitmap(),
                                    contentDescription = "Xem trước",
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(c.mainBg, RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.mime, color = c.textMuted, fontSize = 11.sp, maxLines = 1)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "✕",
                                color = c.danger,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { picked = picked.filterIndexed { i, _ -> i != index } }
                                    .padding(6.dp),
                            )
                        }
                    }
                }
            }
        }

        MeiSectionCard(
            title = "Thông tin",
            subtitle = "Tên tệp giữ nguyên tiếng Việt / ký tự Unicode — chú thích áp dụng cho tất cả",
        ) {
            MeiTextField(caption, { caption = it }, "Chú thích (tùy chọn)", "Mô tả ngắn…")
            Spacer(Modifier.height(10.dp))
            MeiFieldLabel("Thư mục lưu trên desktop")
            MeiChipRow(TARGETS, target) { target = it }
        }

        MeiPrimaryButton(
            text = if (state.sending) "Đang gửi…" else "Gửi ${picked.size} tệp lên desktop",
            loading = state.sending,
            enabled = picked.isNotEmpty() && !state.sending && !reading,
        ) {
            val items = picked
            scope.launch {
                var ok = 0
                var firstMsg = ""
                items.forEach { item ->
                    val msg = withContext(Dispatchers.IO) {
                        state.sendFile(item.file, item.name, target, caption.trim(), item.mime)
                    }
                    if (firstMsg.isBlank()) firstMsg = msg
                    if (msg.startsWith("Đã lưu") || msg.startsWith("Desktop offline")) ok++
                }
                message = if (items.size == 1) {
                    firstMsg
                } else if (ok == items.size) {
                    "Đã gửi $ok tệp lên desktop"
                } else {
                    "Gửi được $ok/${items.size} tệp"
                }
                if (ok == items.size) {
                    picked = emptyList()
                    caption = ""
                }
            }
        }

        MeiMessageBanner(message)
        MeiMessageBanner(state.lastMessage.takeIf { it.isNotBlank() && message.isBlank() })
    }
}

@Composable
private fun ActionBox(glyph: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalMeiColors.current
    Row(
        modifier = modifier
            .background(c.mainBgAlt, RoundedCornerShape(12.dp))
            .border(1.dp, c.borderSoft, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(glyph, fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text(label, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
