package com.zhiend.meiremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiend.meiremote.data.AppState
import com.zhiend.meiremote.data.ChainApp
import com.zhiend.meiremote.data.ChainApps
import com.zhiend.meiremote.ui.components.ShareDialog
import com.zhiend.meiremote.ui.screens.DrawScreen
import com.zhiend.meiremote.ui.screens.HomeScreen
import com.zhiend.meiremote.ui.screens.NotesScreen
import com.zhiend.meiremote.ui.screens.SendScreen
import com.zhiend.meiremote.ui.screens.SettingsScreen
import com.zhiend.meiremote.ui.screens.WebAppsScreen
import com.zhiend.meiremote.ui.screens.WebViewScreen
import com.zhiend.meiremote.ui.theme.LocalMeiColors

enum class MeiTab(val label: String, val glyph: String) {
    HOME("Trang chủ", "⌂"),
    NOTES("Ghi chú", "✎"),
    SEND("Gửi file", "⇪"),
    DRAW("Vẽ", "✎"),
    WEB("Web Apps", "🌐"),
    SETTINGS("Cài đặt", "⚙"),
}

@Composable
fun AppRoot(state: AppState) {
    val c = LocalMeiColors.current
    var tab by rememberSaveable { mutableStateOf(MeiTab.HOME) }

    // Web App đang mở trong WebView (in-app) — null = hiện danh sách hub
    var webAppId by rememberSaveable { mutableStateOf("") }
    var webUrl by rememberSaveable { mutableStateOf("") }
    var webTitle by rememberSaveable { mutableStateOf("") }

    fun openWebApp(app: ChainApp, url: String) {
        webAppId = app.id
        webUrl = url
        webTitle = app.name
        tab = MeiTab.WEB
    }

    fun closeWebApp() {
        webAppId = ""
        webUrl = ""
        webTitle = ""
    }

    Scaffold(
        containerColor = c.mainBg,
        bottomBar = {
            NavigationBar(
                containerColor = c.cardBg,
                tonalElevation = 0.dp,
            ) {
                MeiTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (tab == item) c.accentSoft else Color.Transparent,
                                        RoundedCornerShape(50),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(item.glyph, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        },
                        label = { Text(item.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.accentHover,
                            selectedTextColor = c.accentHover,
                            unselectedIconColor = c.textMuted,
                            unselectedTextColor = c.textMuted,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            when (tab) {
                MeiTab.HOME -> HomeScreen(state, onNavigate = { tab = it })
                MeiTab.NOTES -> NotesScreen(state)
                MeiTab.SEND -> SendScreen(state)
                MeiTab.DRAW -> DrawScreen(state)
                MeiTab.WEB -> {
                    val app = webAppId.takeIf { it.isNotBlank() }?.let { ChainApps.byId(it) }
                    if (app != null && webUrl.isNotBlank()) {
                        WebViewScreen(
                            url = webUrl,
                            title = webTitle.ifBlank { app.name },
                            accent = app.color,
                            onClose = ::closeWebApp,
                        )
                    } else {
                        WebAppsScreen(
                            state = state,
                            onOpenInApp = { chainApp, url -> openWebApp(chainApp, url) },
                        )
                    }
                }
                MeiTab.SETTINGS -> SettingsScreen(state)
            }
        }
    }

    if (state.sharedText != null || state.sharedImageUri != null) {
        ShareDialog(state, onDismiss = {})
    }
}
