package top.abdl_space.app.core.designsystem.theme

/**
 * Pure ARGB tokens, with no Android/Compose dependency (including contrast-test inputs).
 * See docs/v4-design-tokens.md. Brand fills and readable accent text are intentionally distinct.
 */
object V4ColorTokens {
    const val DarkPageBg: Long = 0xFF101010
    const val DarkSurface: Long = 0xFF1C1C1E
    const val DarkSurfaceHigh: Long = 0xFF2C2C2E
    const val DarkSurfaceGlass: Long = 0xE6141414
    const val DarkSurfaceGlassFallback: Long = 0xFF141414
    const val DarkTextPrimary: Long = 0xFFE8E8E8
    const val DarkTextEmphasis: Long = 0xFFFFFFFF
    const val DarkTextSecondary: Long = 0xFF969696
    const val DarkTextTertiary: Long = 0xFF5A5A5C
    const val DarkDivider: Long = 0xFF232325
    const val DarkNavActivePill: Long = 0xFF242426

    const val LightPageBg: Long = 0xFFF6F6F6
    const val LightSurface: Long = 0xFFFFFFFF
    const val LightSurfaceHigh: Long = 0xFFF0F0F0
    const val LightSurfaceGlass: Long = 0xE6FFFFFF
    const val LightSurfaceGlassFallback: Long = 0xFFFFFFFF
    const val LightTextPrimary: Long = 0xFF1A1A1A
    const val LightTextEmphasis: Long = 0xFF1A1A1A
    const val LightTextSecondary: Long = 0xFF676767
    const val LightTextTertiary: Long = 0xFF8E8E8E
    const val LightDivider: Long = 0xFFE8E8E8
    const val LightNavActivePill: Long = 0xFFDCEEFB

    // Brand button/background pair in BOTH themes. White on this blue fails the icon gate.
    const val Accent: Long = 0xFF64BFF2
    const val OnAccent: Long = 0xFF0A141C
    const val AccentContainer: Long = 0xFF1A2A36
    const val LightAccentContainer: Long = 0xFFDCEEFB
    const val LightOnAccentContainer: Long = 0xFF0A3A56
    // Light Material primary/links/tabs; not the brand FAB fill.
    const val LightAccentText: Long = 0xFF1B6A9A
    const val LightOnPrimary: Long = 0xFFFFFFFF

    const val AccentSecondary: Long = 0xFFFFD98E
    const val OnAccentSecondary: Long = 0xFF1A1A1A
    const val Danger: Long = 0xFFF04030
    const val Success: Long = 0xFF30A063
    const val Warning: Long = 0xFFF0B010
}
