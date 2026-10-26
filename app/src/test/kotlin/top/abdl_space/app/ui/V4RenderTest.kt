package top.abdl_space.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/**
 * Real Compose decor hierarchy CPU rendering, not a GPU/device/glass acceptance test.
 * With -Dv4.render.dir=/outside/repository/evidence/renders, writes optional review artifacts.
 * No directory or image is written when that property is absent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V4RenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun darkRoots() = renderRoots(V4ThemePreference.Dark, "dark")
    @Test fun lightRoots() = renderRoots(V4ThemePreference.Light, "light")

    private fun renderRoots(theme: V4ThemePreference, prefix: String) {
        compose.setContent { V4App(themePreference = theme, reduceTransparency = true, reduceMotion = true) }
        val rootTags = mapOf(
            V4TopLevelDestination.Home to V4TestTags.Home,
            V4TopLevelDestination.Discover to V4TestTags.Discover,
            V4TopLevelDestination.Messages to V4TestTags.Messages,
            V4TopLevelDestination.Me to V4TestTags.Me,
        )
        V4TopLevelDestination.entries.forEach { tab ->
            compose.onNode(hasText(tab.label) and hasAnyAncestor(hasTestTag(V4TestTags.BottomNavigation))).performClick()
            compose.onNodeWithTag(rootTags.getValue(tab)).assertExists()
            compose.waitForIdle()
            compose.runOnIdle { renderDecor("$prefix-${tab.route}") }
        }
    }

    private fun renderDecor(name: String) {
        val decor = compose.activity.window.decorView
        assertTrue("The real decor must be measured before drawing", decor.width > 0 && decor.height > 0)
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        try {
            decor.draw(Canvas(bitmap))
            val colors = HashSet<Int>()
            val stepX = (bitmap.width / 64).coerceAtLeast(1)
            val stepY = (bitmap.height / 128).coerceAtLeast(1)
            for (y in 0 until bitmap.height step stepY) {
                for (x in 0 until bitmap.width step stepX) colors += bitmap.getPixel(x, y)
            }
            assertTrue("$name must contain rendered UI, not a blank or flat bitmap", colors.size > 8)
            val output = System.getProperty("v4.render.dir")?.takeIf { it.isNotBlank() } ?: return
            val directory = File(output)
            assertTrue("Render output must use an absolute evidence directory", directory.isAbsolute)
            val projectRootPath = requireNotNull(System.getProperty("v4.project.root")) {
                "Set v4.project.root to validate that render output stays outside the repository"
            }
            val projectRoot = File(projectRootPath).canonicalFile
            assertTrue("Render evidence must remain outside the repository", !directory.canonicalFile.toPath().startsWith(projectRoot.toPath()))
            assertTrue("Evidence directory must be writable", directory.isDirectory || directory.mkdirs())
            File(directory, "$name.png").outputStream().use { stream ->
                assertTrue("PNG compression must succeed", bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
