package top.abdl_space.app.core.designsystem.glass

import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import top.abdl_space.app.core.designsystem.theme.V4Dimens
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/**
 * The ONLY V4 file that links miuix-blur. Its real 0.9.4 AAR signatures were checked with javap.
 * Do not inline these entry points into the API26-compatible facade.
 * Uses the library; no shader, recorder, or legacy bridge implementation is copied here.
 */
@RequiresApi(33)
internal object V4GlassApi33 {
    @Composable
    @DoNotInline
    fun rememberCapture(): V4GlassCapture? {
        if (!isRuntimeShaderSupported()) return null
        val layer = rememberLayerBackdrop()
        val renderer = remember(layer) { Api33Renderer(layer) }
        DisposableEffect(renderer) {
            renderer.isActive = true
            onDispose { renderer.isActive = false }
        }
        return remember(layer, renderer) {
            V4GlassCapture(V4GlassBackdrop(renderer), Modifier.layerBackdrop(layer))
        }
    }
}

@RequiresApi(33)
private class Api33Renderer(private val layer: LayerBackdrop) : V4GlassRenderer {
    override var isActive: Boolean by mutableStateOf(true)

    @DoNotInline
    override fun surfaceModifier(shape: Shape, tint: Color, fallback: Color): Modifier =
        Modifier.drawBackdrop(
            backdrop = layer,
            shape = { shape },
            // This blurs the captured page, not this surface's icons/text.
            effects = { blur(V4Dimens.glassBlurRadius.toPx()) },
            // A missing first capture never leaves a fully transparent, unreadable surface.
            onDrawBehind = { drawRect(fallback) },
            onDrawSurface = { drawRect(tint) },
        )
}
