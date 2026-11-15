package com.zhiend.meiremote.bubble

import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiend.meiremote.MeiApplication
import com.zhiend.meiremote.data.AppState
import com.zhiend.meiremote.ui.components.QuickCapturePanel
import com.zhiend.meiremote.ui.theme.LocalMeiColors
import com.zhiend.meiremote.ui.theme.MeiRemoteTheme

/**
 * Floating quick-note window (~75% of the screen) opened by [BubbleService].
 * Reuses the exact same [QuickCapturePanel] as the Notes tab.
 */
class BubbleOverlayActivity : ComponentActivity() {

    private val state: AppState by lazy { (application as MeiApplication).state }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFormat(PixelFormat.TRANSLUCENT)
        // A true floating panel: transparent outside the card, slightly dimmed
        // behind it, centered, and never larger than ~75% of the screen.
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0.45f)

        val metrics = resources.displayMetrics
        val width = (metrics.widthPixels * 0.75f).toInt()
        val height = (metrics.heightPixels * 0.78f).toInt()
        window.setGravity(Gravity.CENTER)
        window.setLayout(width, height)

        setContent {
            val dark = when (state.themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            MeiRemoteTheme(darkTheme = dark) {
                BubbleOverlayContent(state, onClose = { finish() })
            }
        }
    }
}

@Composable
private fun BubbleOverlayContent(state: AppState, onClose: () -> Unit) {
    val c = LocalMeiColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.mainBg, RoundedCornerShape(20.dp))
            .border(1.dp, c.inputBorder, RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ghi chú nhanh", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Không cần mở app — gửi thẳng lên MeiBrowser", color = c.textMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .background(c.accentSoft, RoundedCornerShape(50))
                    .border(1.dp, c.inputBorder, RoundedCornerShape(50))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text("✕", color = c.accentHover, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(1.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuickCapturePanel(state)
        }
    }
}
