package top.abdl_space.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = V4Application::class)
class V4StartupTest {
    @Test
    fun `manifest uses the Hilt application rather than a plain Application`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        assertTrue(application is V4Application)
        val info = application.packageManager.getApplicationInfo(application.packageName, 0)
        assertEquals(V4Application::class.java.name, info.className)
    }

    @Test
    fun `real activity and Hilt graph start on API 28 fallback`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            assertNotNull(activity.window.decorView)
            assertTrue(!activity.isFinishing)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
