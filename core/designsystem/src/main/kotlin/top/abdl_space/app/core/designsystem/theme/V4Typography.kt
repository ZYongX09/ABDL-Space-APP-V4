package top.abdl_space.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** V4 设计 tokens —— 字体（来源 docs/v4-design-tokens.md §3，行高 1.4×）。 */
object V4TypeTokens {
    val title = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
    val body = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp)
    val bodySecondary = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp)
    val caption = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, lineHeight = 15.sp)
    val tabLabel = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, lineHeight = 13.sp)
}

internal val V4Typography = Typography(
    titleLarge = V4TypeTokens.title,
    titleMedium = V4TypeTokens.title,
    titleSmall = V4TypeTokens.bodySecondary,
    bodyLarge = V4TypeTokens.body,
    bodyMedium = V4TypeTokens.bodySecondary,
    bodySmall = V4TypeTokens.caption,
    labelLarge = V4TypeTokens.bodySecondary,
    labelMedium = V4TypeTokens.caption,
    labelSmall = V4TypeTokens.tabLabel,
)
