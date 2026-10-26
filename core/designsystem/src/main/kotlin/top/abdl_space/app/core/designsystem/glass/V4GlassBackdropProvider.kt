package top.abdl_space.app.core.designsystem.glass

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Opaque, provider-owned capture handle. Public APIs deliberately contain no miuix/API33 types.
 * Do not retain this beyond the provider's composition or use it in the captured [content].
 */
@Stable
class V4GlassBackdrop internal constructor(internal val renderer: V4GlassRenderer)

internal interface V4GlassRenderer {
    val isActive: Boolean
    fun surfaceModifier(shape: Shape, tint: Color, fallback: Color): Modifier
}

/**
 * Captures only [content]; [overlay] is drawn afterwards and must contain glass consumers.
 * Capture and consumer must share a window. Content must extend behind the consuming surface
 * (e.g. an edge-to-edge NavHost, with list content padding rather than a shrunken NavHost).
 *
 * [reduceTransparency] defaults to true: explicitly supply false only after the user's preference
 * is loaded. API < 33, low/unknown RAM, software rendering and this preference all return a null
 * handle without entering the API33 implementation or allocating a capture GraphicsLayer.
 *
 * Example: V4GlassBackdropProvider(content = { NavHost(...) }, overlay = { backdrop ->
 *     V4PillNavigationBar(..., modifier = Modifier.align(Alignment.BottomCenter), backdrop = backdrop)
 * })
 */
@Composable
fun V4GlassBackdropProvider(
    modifier: Modifier = Modifier,
    reduceTransparency: Boolean = true,
    content: @Composable () -> Unit,
    overlay: @Composable BoxScope.(V4GlassBackdrop?) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    // Re-evaluate on recomposition, including when the user's preference changes.
    val isLowRam = readLowRamDevice(context)
    var hardwareAccelerated by remember(view) { mutableStateOf(view.isHardwareAccelerated) }
    DisposableEffect(view) {
        // A newly-created ComposeView can compose before attachment. Observe attach/pre-draw so
        // an initial false cannot permanently suppress glass, and a detached host fails closed.
        val update = android.view.ViewTreeObserver.OnPreDrawListener {
            hardwareAccelerated = view.isHardwareAccelerated
            true
        }
        val observer = view.viewTreeObserver
        observer.addOnPreDrawListener(update)
        val attach = object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) {
                hardwareAccelerated = v.isHardwareAccelerated
            }

            override fun onViewDetachedFromWindow(v: android.view.View) {
                hardwareAccelerated = false
            }
        }
        view.addOnAttachStateChangeListener(attach)
        onDispose {
            val currentObserver = if (observer.isAlive) observer else view.viewTreeObserver
            if (currentObserver.isAlive) currentObserver.removeOnPreDrawListener(update)
            view.removeOnAttachStateChangeListener(attach)
        }
    }
    val eligible = LiquidGlassPolicy.canUseGlass(
        sdkInt = Build.VERSION.SDK_INT,
        isLowRamDevice = isLowRam,
        reduceTransparency = reduceTransparency,
        isHardwareAccelerated = hardwareAccelerated,
    )
    // Explicit SDK check: the verifier/optimizer must not move API33 code into this function.
    val capture = if (Build.VERSION.SDK_INT >= LiquidGlassPolicy.MIN_GLASS_API && eligible) {
        V4GlassApi33.rememberCapture()
    } else {
        null
    }
    Box(modifier) {
        Box(modifier = capture?.modifier ?: Modifier) {
            content()
        }
        overlay(capture?.backdrop)
    }
}

internal class V4GlassCapture(val backdrop: V4GlassBackdrop, val modifier: Modifier)

internal fun readLowRamDevice(context: Context): Boolean? = try {
    context.getSystemService(ActivityManager::class.java)?.isLowRamDevice
} catch (_: RuntimeException) {
    // A missing service or failure to inspect it is not permission to run a shader.
    null
}
