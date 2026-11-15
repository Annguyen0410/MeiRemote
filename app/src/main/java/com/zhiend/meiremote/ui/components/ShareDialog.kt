package com.zhiend.meiremote.ui.components

import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiend.meiremote.data.AppState
import com.zhiend.meiremote.data.extractUrl
import com.zhiend.meiremote.ui.theme.LocalMeiColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Shown when another app shares text/URL/image into Mei Remote. */
@Composable
fun ShareDialog(state: AppState, onDismiss: () -> Unit) {
    val c = LocalMeiColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val text = state.sharedText
    val imageUri = state.sharedImageUri
    val url = text?.let { extractUrl(it) }

    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    fun dismiss() {
        state.consumeShare()
        onDismiss()
    }

    fun sendFileFromUri(uriString: String) {
        scope.launch {
            busy = true
            result = withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(uriString)
                    val name = displayName(context, uri) ?: "anh-${System.currentTimeMillis()}.jpg"
                    val dir = File(context.cacheDir, "mei_share").apply { mkdirs() }
                    val tmp = File(dir, name)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    state.sendFile(tmp, name, "Photos", "Chia sẻ từ app khác", mime)
                } catch (e: Exception) {
                    "Không đọc được ảnh"
                }
            }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) dismiss() },
        containerColor = c.cardBg,
        titleContentColor = c.text,
        textContentColor = c.text,
        title = { Text("Nhận từ app khác", fontSize = 17.sp) },
        text = {
            Column {
                if (text != null) {
                    Text(
                        text,
                        fontSize = 13.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        color = c.textMuted,
                    )
                } else if (imageUri != null) {
                    Text("Hình ảnh được chia sẻ", fontSize = 13.sp, color = c.textMuted)
                }
                if (result != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(result!!, fontSize = 12.sp, color = c.accentHover)
                }
                Spacer(Modifier.height(12.dp))
                if (url != null) {
                    Button(onClick = {
                        scope.launch {
                            busy = true
                            result = state.savePage(url, text!!.replace(url, "").trim().ifBlank { url })
                            busy = false
                        }
                    }, enabled = !busy, modifier = Modifier.padding(bottom = 6.dp)) {
                        Text("Lưu trang vào Library")
                    }
                }
                if (text != null) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            busy = true
                            result = state.sendNote(state.prefs.defaultCategory, text.take(60), text, emptyList())
                            busy = false
                        }
                    }, enabled = !busy, modifier = Modifier.padding(bottom = 6.dp)) {
                        Text("Ghi chú")
                    }
                    OutlinedButton(onClick = {
                        scope.launch {
                            busy = true
                            result = state.sendTask(text.take(120), "Inbox", "", "")
                            busy = false
                        }
                    }, enabled = !busy, modifier = Modifier.padding(bottom = 6.dp)) {
                        Text("Nhiệm vụ")
                    }
                }
                if (imageUri != null) {
                    Button(onClick = { sendFileFromUri(imageUri) }, enabled = !busy) {
                        Text("Gửi ảnh lên desktop")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { dismiss() }, enabled = !busy) {
                Text("Xong", color = c.accentHover)
            }
        },
        dismissButton = {
            TextButton(onClick = { dismiss() }, enabled = !busy) {
                Text("Hủy", color = c.textMuted)
            }
        },
    )
}

private fun displayName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    } catch (_: Exception) {
        null
    }
}
