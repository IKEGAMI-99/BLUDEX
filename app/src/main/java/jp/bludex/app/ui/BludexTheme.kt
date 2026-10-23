package jp.bludex.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Night = Color(0xFF08111E)
val Panel = Color(0xFF101F33)
val CardBlue = Color(0xFF142941)
val Sky = Color(0xFF65CDFF)
val Muted = Color(0xFF96A8BF)
val Green = Color(0xFF72DDB7)
val Amber = Color(0xFFFFD37B)

private val colors = darkColorScheme(
    primary = Sky, onPrimary = Night, secondary = Green,
    background = Night, onBackground = Color(0xFFF3F8FF),
    surface = Panel, onSurface = Color(0xFFF3F8FF),
    surfaceVariant = CardBlue, onSurfaceVariant = Muted,
    outline = Color(0xFF324A62)
)
@Composable
fun BludexTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
