package com.zhiend.meiremote.ui.screens

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zhiend.meiremote.ui.theme.LocalMeiColors

/**
 * Mở một web app của Project Chain ngay trong MeiRemote (không rời khỏi app):
 * thanh điều hướng nhỏ với ⬅ ➡ ⟳ và ❐ thoát / mở trình duyệt ngoài.
 */
@Composable
fun WebViewScreen(
    url: String,
    title: String,
    accent: Long,
    onClose: () -> Unit,
) {
    val c = LocalMeiColors.current
    val context = LocalContext.current

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(AndroidColor.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportZoom(true)
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
    }

    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }

    Column(Modifier.fillMaxSize()) {
        // Top bar mini-browser
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.cardBg)
                .border(1.dp, c.borderSoft, RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(c.mainBgAlt, RoundedCornerShape(9.dp))
                    .clickable { webView.goBack() }
                    .padding(top = 7.dp),
                contentAlignment = Alignment.TopCenter,
            ) { Text("‹", color = c.text, fontSize = 22.sp) }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(c.mainBgAlt, RoundedCornerShape(9.dp))
                    .clickable { webView.goForward() }
                    .padding(top = 7.dp),
                contentAlignment = Alignment.TopCenter,
            ) { Text("›", color = c.text, fontSize = 22.sp) }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(c.mainBgAlt, RoundedCornerShape(9.dp))
                    .clickable { webView.reload() }
                    .padding(top = 6.dp),
                contentAlignment = Alignment.TopCenter,
            ) { Text("⟳", color = c.text, fontSize = 18.sp) }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = c.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    url,
                    color = c.textMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(c.mainBgAlt, RoundedCornerShape(9.dp))
                    .clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(webView.url ?: url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    .padding(top = 8.dp),
                contentAlignment = Alignment.TopCenter,
            ) { Text("🔗", fontSize = 15.sp) }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(Color(accent).copy(alpha = 0.18f), RoundedCornerShape(9.dp))
                    .clickable(onClick = onClose)
                    .padding(top = 8.dp),
                contentAlignment = Alignment.TopCenter,
            ) { Text("✕", color = Color(accent), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }

        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            update = { it.loadUrl(url) },
        )
    }
}