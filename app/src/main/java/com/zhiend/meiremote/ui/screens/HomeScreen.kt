package com.zhiend.meiremote.ui.screens

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiend.meiremote.data.AppState
import com.zhiend.meiremote.ui.MeiTab
import com.zhiend.meiremote.ui.components.MeiMessageBanner
import com.zhiend.meiremote.ui.components.MeiPageHeader
import com.zhiend.meiremote.ui.components.MeiPrimaryButton
import com.zhiend.meiremote.ui.components.MeiSectionCard
import com.zhiend.meiremote.ui.components.MeiStatusBanner
import com.zhiend.meiremote.ui.components.MeiTextField
import com.zhiend.meiremote.ui.theme.LocalMeiColors
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(state: AppState, onNavigate: (MeiTab) -> Unit) {
    val c = LocalMeiColors.current
    val scope = rememberCoroutineScope()
    var quickText by rememberSaveable { mutableStateOf("") }
    var quickMessage by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MeiPageHeader("Mei Remote", "Ghi chú, gửi file, vẽ và điều khiển — đồng bộ về máy tính")

        MeiStatusBanner(state.status, state.statusText, onTap = {
            scope.launch { state.testConnection() }
        })

        MeiSectionCard(
            title = "Gửi nhanh",
            subtitle = "Gõ 1 dòng: link → lưu trang · /task … → nhiệm vụ · còn lại → ghi chú",
        ) {
            MeiTextField(
                quickText,
                { quickText = it },
                "Nhập nhanh…",
                "https://… hoặc /task … hoặc ghi chú",
            )
            Spacer(Modifier.height(8.dp))
            MeiPrimaryButton(
                text = if (state.sending) "Đang gửi…" else "Gửi",
                loading = state.sending,
                enabled = quickText.isNotBlank() && !state.sending,
            ) {
                scope.launch {
                    quickMessage = state.quickCapture(quickText)
                    if (quickMessage.startsWith("Đã lưu") || quickMessage.startsWith("Đã gửi") || quickMessage.startsWith("Desktop offline")) {
                        quickText = ""
                    }
                }
            }
        }

        MeiMessageBanner(quickMessage)
        MeiMessageBanner(state.lastMessage.takeIf { it.isNotBlank() && quickMessage.isBlank() })

        MeiSectionCard(
            title = "Hành động nhanh",
            subtitle = "Nhảy thẳng vào từng công cụ",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickTile("✎", "Ghi chú", "Note / Task / Link", Modifier.weight(1f)) { onNavigate(MeiTab.NOTES) }
                QuickTile("⇪", "Gửi file", "Ảnh, PDF…", Modifier.weight(1f)) { onNavigate(MeiTab.SEND) }
                QuickTile("✏", "Vẽ", "Bản vẽ tay", Modifier.weight(1f)) { onNavigate(MeiTab.DRAW) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickTile("🌐", "Web Apps", "6 app + hub", Modifier.weight(1f)) { onNavigate(MeiTab.WEB) }
                QuickTile("⌂", "Trang chủ", "Công cụ điều khiển", Modifier.weight(1f)) { onNavigate(MeiTab.HOME) }
            }
        }

        MeiSectionCard(
            title = "Hàng chờ gửi",
            subtitle = if (state.outboxCount > 0)
                "Có ${state.outboxCount} mục chưa gửi — sẽ tự động lên khi desktop online"
            else
                "Không có mục nào chờ — mọi thứ đã ở trên desktop",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(if (state.outboxCount > 0) c.accentSoft else c.mainBgAlt, RoundedCornerShape(10.dp))
                        .border(1.dp, c.inputBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        state.outboxCount.toString(),
                        color = if (state.outboxCount > 0) c.accentHover else c.textMuted,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.outboxCount > 0) "Đang chờ đồng bộ" else "Đã đồng bộ hết",
                        color = c.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Tự động gửi khi cả hai máy online",
                        color = c.textMuted,
                        fontSize = 11.sp,
                    )
                }
                Text(
                    "Đồng bộ",
                    color = c.accentHover,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(c.accentSoft, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable { scope.launch { state.flushOutbox() } },
                )
            }
        }

        MeiSectionCard(
            title = "Hướng dẫn nhanh",
            subtitle = "Kết nối hai máy qua Wi-Fi cùng mạng",
        ) {
            Text(
                "1. Desktop: Cài đặt → Mobile / Android bridge → bật receiver + “Listen on LAN”.\n" +
                    "2. Ghi lại token và IPv4 của máy tính.\n" +
                    "3. Tab Cài đặt trong app: nhập host/port/token → Lưu & kiểm tra.\n" +
                    "4. Ghi chú, nhiệm vụ, link, ảnh hay bản vẽ tự lưu vào SafeVault trên desktop.",
                color = c.textMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun QuickTile(glyph: String, title: String, hint: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalMeiColors.current
    Column(
        modifier = modifier
            .background(c.mainBgAlt, RoundedCornerShape(12.dp))
            .border(1.dp, c.borderSoft, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Text(glyph, color = c.accentHover, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(title, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(hint, color = c.textMuted, fontSize = 10.sp)
    }
}
