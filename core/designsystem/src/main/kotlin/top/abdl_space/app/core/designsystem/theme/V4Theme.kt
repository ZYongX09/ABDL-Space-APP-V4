package top.abdl_space.app.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

private fun color(v: Long): Color = Color(v)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = color(V4ColorTokens.Accent),
    onPrimary = color(V4ColorTokens.OnAccent),
    primaryContainer = color(V4ColorTokens.AccentContainer),
    onPrimaryContainer = color(V4ColorTokens.Accent),
    secondary = color(V4ColorTokens.AccentSecondary),
    onSecondary = color(V4ColorTokens.OnAccentSecondary),
    tertiary = color(V4ColorTokens.Success),
    background = color(V4ColorTokens.DarkPageBg),
    onBackground = color(V4ColorTokens.DarkTextPrimary),
    surface = color(V4ColorTokens.DarkSurface),
    onSurface = color(V4ColorTokens.DarkTextPrimary),
    surfaceVariant = color(V4ColorTokens.DarkSurfaceHigh),
    onSurfaceVariant = color(V4ColorTokens.DarkTextSecondary),
    outline = color(V4ColorTokens.DarkDivider),
    outlineVariant = color(V4ColorTokens.DarkDivider),
    error = color(V4ColorTokens.Danger),
    surfaceContainerLow = color(V4ColorTokens.DarkPageBg),
    surfaceContainer = color(V4ColorTokens.DarkSurface),
    surfaceContainerHigh = color(V4ColorTokens.DarkSurfaceHigh),
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = color(V4ColorTokens.LightAccentText),
    onPrimary = color(V4ColorTokens.LightOnPrimary),
    primaryContainer = color(V4ColorTokens.LightAccentContainer),
    onPrimaryContainer = color(V4ColorTokens.LightOnAccentContainer),
    secondary = color(V4ColorTokens.AccentSecondary),
    onSecondary = color(V4ColorTokens.OnAccentSecondary),
    tertiary = color(V4ColorTokens.Success),
    background = color(V4ColorTokens.LightPageBg),
    onBackground = color(V4ColorTokens.LightTextPrimary),
    surface = color(V4ColorTokens.LightSurface),
    onSurface = color(V4ColorTokens.LightTextPrimary),
    surfaceVariant = color(V4ColorTokens.LightSurfaceHigh),
    onSurfaceVariant = color(V4ColorTokens.LightTextSecondary),
    outline = color(V4ColorTokens.LightDivider),
    outlineVariant = color(V4ColorTokens.LightDivider),
    error = color(V4ColorTokens.Danger),
    surfaceContainerLow = color(V4ColorTokens.LightPageBg),
    surfaceContainer = color(V4ColorTokens.LightSurface),
    surfaceContainerHigh = color(V4ColorTokens.LightSurfaceHigh),
)

/** 深色优先主题；浅色从 furryfurry 参考适配。 */
@Composable
fun V4Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalV4Colors provides if (darkTheme) DarkV4Colors else LightV4Colors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = V4Typography,
            content = content,
        )
    }
}

/** V4-specific semantic colors, including glass/fallback and brand fill versus accent text. */
object V4Theme {
    val colors: V4Colors
        @Composable
        @ReadOnlyComposable
        get() = LocalV4Colors.current
}

/** 语义文字样式入口（tokens §3）。 */
object V4Text {
    val title get() = V4TypeTokens.title
    val body get() = V4TypeTokens.body
    val bodySecondary get() = V4TypeTokens.bodySecondary
    val caption get() = V4TypeTokens.caption
    val tabLabel get() = V4TypeTokens.tabLabel
}
