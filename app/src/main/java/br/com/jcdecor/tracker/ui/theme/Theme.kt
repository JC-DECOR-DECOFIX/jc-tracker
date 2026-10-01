package br.com.jcdecor.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF142421)
private val Primary = Color(0xFF1F6B62)
private val PrimaryDark = Color(0xFF123C3A)
private val Background = Color(0xFFF3F5F4)
private val Error = Color(0xFF8E2F2F)

private val Colors = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    secondary = PrimaryDark,
    onSecondary = Color.White,
    background = Background,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    error = Error,
    onError = Color.White,
)

@Composable
fun JCTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
