package top.abdl_space.app.core.designsystem.glass

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import top.abdl_space.app.core.designsystem.theme.V4Theme

/** Executes real Compose fallback and reads pixels, but deliberately does NOT claim AGSL parity. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 28, 33], qualifiers = "w360dp-h800dp")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V4GlassFallbackTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `low RAM provider keeps both page and overlay and passes no capture handle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(ActivityManager::class.java)
        shadowOf(manager).setIsLowRamDevice(true)
        assertEquals(true, readLowRamDevice(context))
        var sawOverlay = false
        var handle: V4GlassBackdrop? = null
        compose.setContent {
            V4Theme(darkTheme = true) {
                V4GlassBackdropProvider(
                    reduceTransparency = false,
                    content = { Text("Captured page") },
                    overlay = {
                        SideEffect { sawOverlay = true; handle = it }
                        V4GlassSurface(it, Modifier.size(80.dp).testTag("fallback")) {}
                    },
                )
            }
        }
        compose.onNodeWithText("Captured page").assertExists()
        compose.runOnIdle { assertTrue(sawOverlay); assertNull(handle) }
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("fallback").captureToImage().toPixelMap()
        val center = pixels[pixels.width / 2, pixels.height / 2]
        assertEquals(1f, center.alpha, 0f)
        assertEquals(20 / 255f, center.red, 1 / 255f)
        assertEquals(20 / 255f, center.green, 1 / 255f)
        assertEquals(20 / 255f, center.blue, 1 / 255f)
    }

    @Test
    fun `default unloaded transparency preference keeps opaque surface on theme change`() {
        val darkTheme = mutableStateOf(true)
        compose.setContent {
            V4Theme(darkTheme = darkTheme.value) {
                V4GlassBackdropProvider(
                    content = { Box(Modifier.size(100.dp)) },
                    overlay = { backdrop ->
                        V4GlassSurface(backdrop, Modifier.size(80.dp).testTag("surface")) {
                            Text("Foreground")
                        }
                    },
                )
            }
        }
        compose.onNodeWithText("Foreground").assertExists()
        // V4Theme's flag is darkTheme: true -> false is the intended dark -> light switch.
        compose.runOnIdle { darkTheme.value = false }
        // runOnIdle waits BEFORE its mutation; wait again for recomposition/layout/draw after it.
        compose.waitForIdle()
        // No content at this corner: solid light fallback must not become transparent on theme change.
        val pixels = compose.onNodeWithTag("surface").captureToImage().toPixelMap()
        val pixel = pixels[pixels.width / 2, pixels.height * 3 / 4]
        assertEquals(1f, pixel.alpha, 0f)
        assertEquals(1f, pixel.red, 1 / 255f)
        assertEquals(1f, pixel.green, 1 / 255f)
        assertEquals(1f, pixel.blue, 1 / 255f)
    }

    @Test
    fun `device adapter reads the actual low RAM flag not a total memory threshold`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(ActivityManager::class.java)
        shadowOf(manager).setIsLowRamDevice(true)
        assertEquals(true, readLowRamDevice(context))
        shadowOf(manager).setIsLowRamDevice(false)
        assertEquals(false, readLowRamDevice(context))
        assertFalse(LiquidGlassPolicy.canUseGlass(33, readLowRamDevice(context)))
    }
}
