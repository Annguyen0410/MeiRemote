package com.zhiend.meiremote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalMeiColors = staticCompositionLocalOf { LightMeiColors }

@Composable
fun MeiRemoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkMeiColors else LightMeiColors
    val colorScheme = (if (darkTheme) darkColorScheme() else lightColorScheme()).copy(
        primary = colors.accent,
        onPrimary = colors.buttonText,
        primaryContainer = colors.accentSoft,
        onPrimaryContainer = colors.text,
        secondary = colors.accentHover,
        onSecondary = colors.buttonText,
        secondaryContainer = colors.menuItemSel,
        onSecondaryContainer = colors.text,
        tertiary = colors.success,
        background = colors.mainBg,
        onBackground = colors.text,
        surface = colors.cardBg,
        onSurface = colors.text,
        surfaceVariant = colors.mainBgAlt,
        onSurfaceVariant = colors.textMuted,
        surfaceContainer = colors.cardBg,
        surfaceContainerHigh = colors.cardBg,
        surfaceContainerHighest = colors.itemHover,
        surfaceContainerLow = colors.mainBgAlt,
        surfaceContainerLowest = colors.inputBg,
        outline = colors.inputBorder,
        outlineVariant = colors.borderSoft,
        error = colors.danger,
        onError = colors.buttonText,
        errorContainer = colors.accentSoft,
        onErrorContainer = colors.danger,
        inverseSurface = colors.text,
        inverseOnSurface = colors.mainBg,
        inversePrimary = colors.accentHover,
    )

    CompositionLocalProvider(LocalMeiColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
