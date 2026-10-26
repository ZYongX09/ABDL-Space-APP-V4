package top.abdl_space.app.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic V4 colors not represented by Material's roles. Never infer these from luminance. */
@Immutable
class V4Colors internal constructor(
    val pageBg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceGlass: Color,
    val surfaceGlassFallback: Color,
    val textPrimary: Color,
    val textEmphasis: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val navActivePill: Color,
    val danger: Color,
    val success: Color,
    val warning: Color,
)

internal val DarkV4Colors = V4Colors(
    pageBg = Color(V4ColorTokens.DarkPageBg),
    surface = Color(V4ColorTokens.DarkSurface),
    surfaceHigh = Color(V4ColorTokens.DarkSurfaceHigh),
    surfaceGlass = Color(V4ColorTokens.DarkSurfaceGlass),
    surfaceGlassFallback = Color(V4ColorTokens.DarkSurfaceGlassFallback),
    textPrimary = Color(V4ColorTokens.DarkTextPrimary),
    textEmphasis = Color(V4ColorTokens.DarkTextEmphasis),
    textSecondary = Color(V4ColorTokens.DarkTextSecondary),
    textTertiary = Color(V4ColorTokens.DarkTextTertiary),
    divider = Color(V4ColorTokens.DarkDivider),
    accent = Color(V4ColorTokens.Accent),
    onAccent = Color(V4ColorTokens.OnAccent),
    accentText = Color(V4ColorTokens.Accent),
    accentContainer = Color(V4ColorTokens.AccentContainer),
    onAccentContainer = Color(V4ColorTokens.Accent),
    navActivePill = Color(V4ColorTokens.DarkNavActivePill),
    danger = Color(V4ColorTokens.Danger),
    success = Color(V4ColorTokens.Success),
    warning = Color(V4ColorTokens.Warning),
)

internal val LightV4Colors = V4Colors(
    pageBg = Color(V4ColorTokens.LightPageBg),
    surface = Color(V4ColorTokens.LightSurface),
    surfaceHigh = Color(V4ColorTokens.LightSurfaceHigh),
    surfaceGlass = Color(V4ColorTokens.LightSurfaceGlass),
    surfaceGlassFallback = Color(V4ColorTokens.LightSurfaceGlassFallback),
    textPrimary = Color(V4ColorTokens.LightTextPrimary),
    textEmphasis = Color(V4ColorTokens.LightTextEmphasis),
    textSecondary = Color(V4ColorTokens.LightTextSecondary),
    textTertiary = Color(V4ColorTokens.LightTextTertiary),
    divider = Color(V4ColorTokens.LightDivider),
    accent = Color(V4ColorTokens.Accent),
    onAccent = Color(V4ColorTokens.OnAccent),
    accentText = Color(V4ColorTokens.LightAccentText),
    accentContainer = Color(V4ColorTokens.LightAccentContainer),
    onAccentContainer = Color(V4ColorTokens.LightOnAccentContainer),
    navActivePill = Color(V4ColorTokens.LightNavActivePill),
    danger = Color(V4ColorTokens.Danger),
    success = Color(V4ColorTokens.Success),
    warning = Color(V4ColorTokens.Warning),
)

internal val LocalV4Colors = staticCompositionLocalOf { DarkV4Colors }
