package com.jex.maclauncher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MacBlue = Color(0xFF2F6FEB)
val MacRed = Color(0xFFFF5F57)
val MacYellow = Color(0xFFFEBC2E)
val MacGreen = Color(0xFF28C840)
val PanelBg = Color(0xF2F6F6F6)
val PanelText = Color(0xFF1C1C1E)
val PanelSubText = Color(0xFF8E8E93)

private val LightScheme = lightColorScheme(
    primary = MacBlue,
    background = Color.Transparent,
    surface = PanelBg,
    onSurface = PanelText,
)

@Composable
fun MacLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightScheme, content = content)
}
