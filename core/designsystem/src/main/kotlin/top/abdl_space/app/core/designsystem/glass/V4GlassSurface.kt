package top.abdl_space.app.core.designsystem.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import top.abdl_space.app.core.designsystem.theme.V4Dimens
import top.abdl_space.app.core.designsystem.theme.V4Theme

/**
 * A clipped backdrop consumer with crisp foreground content. Use a handle from a sibling provider
 * overlay, never from within the capture itself. No handle (also the source-compatible default)
 * means an opaque, bordered fallback; there is no self-blur or globally transparent Surface.
 */
@Composable
fun V4GlassSurface(
    backdrop: V4GlassBackdrop? = null,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(V4Dimens.cardRadius),
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = V4Theme.colors
    val renderer = backdrop?.renderer
    val surfaceModifier = if (
        Build.VERSION.SDK_INT >= LiquidGlassPolicy.MIN_GLASS_API && renderer?.isActive == true
    ) {
        renderer.surfaceModifier(shape, colors.surfaceGlass, colors.surfaceGlassFallback)
    } else {
        Modifier.background(colors.surfaceGlassFallback, shape)
    }
    CompositionLocalProvider(LocalContentColor provides colors.textPrimary) {
        Box(
            modifier = modifier
                .clip(shape)
                .then(surfaceModifier)
                .border(V4Dimens.glassBorder, colors.divider, shape),
            content = content,
        )
    }
}
