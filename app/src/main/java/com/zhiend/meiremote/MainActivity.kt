package com.zhiend.meiremote

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.zhiend.meiremote.bubble.BubbleService
import com.zhiend.meiremote.data.AppState
import com.zhiend.meiremote.ui.AppRoot
import com.zhiend.meiremote.ui.theme.MeiRemoteTheme

class MainActivity : ComponentActivity() {
    private val state: AppState by lazy { (application as MeiApplication).state }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Re-show the bubble after a force-stop if it was enabled.
        if (state.prefs.bubbleEnabled && Settings.canDrawOverlays(this)) {
            BubbleService.start(this)
        }
        handleShareIntent(intent)
        setContent {
            val dark = when (state.themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            MeiRemoteTheme(darkTheme = dark) {
                AppRoot(state)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val type = intent.type ?: ""
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        var imageUri: String? = null
        if (type.startsWith("image/")) {
            val uri = getStreamUri(intent)
            if (uri != null) imageUri = uri.toString()
        }
        if (text != null || imageUri != null) {
            state.acceptShare(text, imageUri)
        }
    }

    @Suppress("DEPRECATION")
    private fun getStreamUri(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    MeiRemoteTheme {
        AppRoot(AppState(LocalContext.current.applicationContext))
    }
}

