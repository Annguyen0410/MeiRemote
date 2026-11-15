package com.zhiend.meiremote.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiend.meiremote.bubble.BubbleService
import com.zhiend.meiremote.data.AppState
import com.zhiend.meiremote.data.PairingCode
import com.zhiend.meiremote.ui.components.MeiChipRow
import com.zhiend.meiremote.ui.components.MeiFieldLabel
import com.zhiend.meiremote.ui.components.MeiMessageBanner
import com.zhiend.meiremote.ui.components.MeiPageHeader
import com.zhiend.meiremote.ui.components.MeiSectionCard
import com.zhiend.meiremote.ui.components.MeiTextField
import com.zhiend.meiremote.ui.theme.LocalMeiColors
import kotlinx.coroutines.launch

private val THEME_MODES = listOf("system", "light", "dark")
private val THEME_LABELS = mapOf("system" to "Theo hệ thống", "light" to "Sáng (minimal)", "dark" to "Tối (minimal-night)")

@Composable
fun SettingsScreen(state: AppState) {
    val c = LocalMeiColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var host by rememberSaveable { mutableStateOf(state.prefs.host) }
    var port by rememberSaveable { mutableStateOf(state.prefs.port.toString()) }
    var token by rememberSaveable { mutableStateOf(state.prefs.token) }
    var defaultCategory by rememberSaveable { mutableStateOf(state.prefs.defaultCategory) }
    var message by rememberSaveable { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }

    fun saveConnection() {
        state.prefs.host = host
        state.prefs.port = port.toIntOrNull() ?: 18444
        state.prefs.token = token
        state.prefs.defaultCategory = defaultCategory.trim().ifBlank { "Personal" }
    }

    fun applyPairing(info: PairingCode.Info) {
        state.prefs.host = info.host
        state.prefs.port = info.port
        state.prefs.token = info.token
        host = info.host
        port = info.port.toString()
        token = info.token
        message = "Đã ghép nối với ${info.host}:${info.port} — đang kiểm tra…"
        scope.launch {
            checking = true
            state.testConnection()
            checking = false
        }
    }

    val qrLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val code = result.data?.getStringExtra(QrScanActivity.EXTRA_CODE)
        if (code != null) {
            val info = PairingCode.parse(code)
            if (info != null) {
                applyPairing(info)
            } else {
                message = "Mã QR không hợp lệ"
            }
        }
    }

    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Returned from the overlay-permission screen; try to start if granted now.
        if (Settings.canDrawOverlays(context)) {
            state.prefs.bubbleEnabled = true
            BubbleService.start(context)
            message = "Bubble đã bật"
        }
    }

    fun toggleBubble(enable: Boolean) {
        if (enable) {
            if (Settings.canDrawOverlays(context)) {
                state.prefs.bubbleEnabled = true
                BubbleService.start(context)
                message = "Bubble ghi chú nhanh đang hiển thị"
            } else {
                overlayLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    )
                )
            }
        } else {
            state.prefs.bubbleEnabled = false
            BubbleService.stop(context)
            message = "Đã tắt bubble"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MeiPageHeader("Cài đặt", "Kết nối với desktop và tùy chỉnh giao diện")

        MeiSectionCard(
            title = "Kết nối desktop",
            subtitle = "Host/port/token lấy từ Cài đặt → Mobile / Android bridge trên máy tính",
        ) {
            MeiTextField(host, { host = it }, "Địa chỉ máy tính (IPv4)", "vd: 192.168.1.25")
            Spacer(Modifier.height(8.dp))
            MeiTextField(
                port,
                { port = it },
                "Cổng",
                "18444",
                keyboardType = KeyboardType.Number,
            )
            Spacer(Modifier.height(8.dp))
            MeiTextField(token, { token = it }, "Token (Bearer)", "Dán token chia sẻ…")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        saveConnection()
                        scope.launch {
                            checking = true
                            state.testConnection()
                            checking = false
                        }
                    },
                    enabled = !checking,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (checking) "Đang kiểm tra…" else "Lưu & kiểm tra", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        saveConnection()
                        scope.launch { state.testConnection() }
                    },
                    enabled = !checking,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Kiểm tra lại", fontWeight = FontWeight.Bold)
                }
            }
        }

        MeiSectionCard(
            title = "Ghép nối nhanh",
            subtitle = "Quét mã QR hoặc dán mã từ MeiBrowser (Quick pairing)",
        ) {
            var pairingText by rememberSaveable { mutableStateOf("") }
            MeiTextField(
                pairingText,
                { pairingText = it },
                "Dán mã ghép nối",
                "MEI1|192.168.1.25|18444|…",
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val info = PairingCode.parse(pairingText)
                        if (info != null) {
                            applyPairing(info)
                            pairingText = ""
                        } else {
                            message = "Mã ghép nối không hợp lệ"
                        }
                    },
                    enabled = pairingText.isNotBlank() && !checking,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Kết nối bằng mã", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { qrLauncher.launch(Intent(context, QrScanActivity::class.java)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Quét mã QR", fontWeight = FontWeight.Bold)
                }
            }
        }

        MeiSectionCard(
            title = "Bubble ghi chú nhanh",
            subtitle = "Bubble nổi trên màn hình — chạm để ghi chú, không cần mở app",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hiện bubble trên màn hình", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Mở cửa sổ ghi chú ~75% màn hình", color = c.textMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = state.prefs.bubbleEnabled,
                    onCheckedChange = { toggleBubble(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = c.cardBg,
                        checkedTrackColor = c.accent,
                        uncheckedThumbColor = c.cardBg,
                        uncheckedTrackColor = c.inputBorder,
                    ),
                )
            }
        }

        MeiSectionCard(
            title = "Giao diện",
            subtitle = "Giao diện y hệt trình duyệt trên máy tính (minimal / minimal-night)",
        ) {
            MeiChipRow(THEME_MODES, state.themeMode) { mode ->
                state.applyThemeMode(mode)
            }
            Text(
                THEME_LABELS[state.themeMode] ?: "Theo hệ thống",
                color = c.textMuted,
                fontSize = 12.sp,
            )
        }

        MeiSectionCard(
            title = "Đồng bộ tự động",
            subtitle = "Khi cả hai máy online, mọi mục chờ sẽ tự động gửi lên desktop",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tự động gửi hàng chờ", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Kiểm tra mỗi 20 giây", color = c.textMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = state.prefs.autoSync,
                    onCheckedChange = { state.prefs.autoSync = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = c.cardBg,
                        checkedTrackColor = c.accent,
                        uncheckedThumbColor = c.cardBg,
                        uncheckedTrackColor = c.inputBorder,
                    ),
                )
            }
            Spacer(Modifier.height(8.dp))
            MeiFieldLabel("Danh mục mặc định cho ghi chú nhanh")
            MeiTextField(defaultCategory, { defaultCategory = it }, "Danh mục", "Personal")
        }

        MeiSectionCard(
            title = "Hàng chờ & dữ liệu",
            subtitle = "Quản lý các mục chưa gửi lên desktop",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${state.outboxCount} mục đang chờ",
                    color = c.text,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Xóa hàng chờ",
                    color = c.danger,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            state.clearOutbox()
                            message = "Đã xóa hàng chờ"
                        }
                        .padding(6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Lần kiểm tra gần nhất: ${if (state.prefs.lastPingOk) "thành công" else "chưa thành công"}",
                color = c.textMuted,
                fontSize = 11.sp,
            )
        }

        Text(
            "Bridge: ${state.prefs.bridgeVersion.ifBlank { "?" }} · " +
                "protocol ${if (state.prefs.protocolVersion > 0) state.prefs.protocolVersion.toString() else "?"} · " +
                (if (state.prefs.bridgeMode == "lan") "LAN" else if (state.prefs.bridgeMode.isBlank()) "?" else "chỉ máy này"),
            color = c.textMuted,
            fontSize = 11.sp,
        )
        Text(
            "Hành động desktop nhận: " +
                (if (state.capabilities.isEmpty()) "chưa kiểm tra" else state.capabilities.size.toString() + " action"),
            color = c.textMuted,
            fontSize = 11.sp,
        )

        MeiMessageBanner(message)
    }
}
