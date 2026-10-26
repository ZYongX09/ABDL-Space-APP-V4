package top.abdl_space.app.core.designsystem.theme

import kotlin.math.pow
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG sRGB contrast, including the worst-case backdrop composites, not just the page color. */
class ContrastTest {
    @Test
    fun `primary secondary and accent text pass on every supported reading surface`() {
        val darkBackgrounds = listOf(
            V4ColorTokens.DarkPageBg, V4ColorTokens.DarkSurface, V4ColorTokens.DarkSurfaceHigh,
            V4ColorTokens.DarkNavActivePill, V4ColorTokens.DarkSurfaceGlassFallback,
            composite(V4ColorTokens.DarkSurfaceGlass, V4ColorTokens.LightSurface),
        )
        val lightBackgrounds = listOf(
            V4ColorTokens.LightPageBg, V4ColorTokens.LightSurface, V4ColorTokens.LightSurfaceHigh,
            V4ColorTokens.LightNavActivePill, V4ColorTokens.LightSurfaceGlassFallback,
            composite(V4ColorTokens.LightSurfaceGlass, 0xFF000000),
        )
        listOf(V4ColorTokens.DarkTextPrimary, V4ColorTokens.DarkTextSecondary, V4ColorTokens.Accent)
            .forEach { fg -> darkBackgrounds.forEach { bg -> assertReadable(fg, bg) } }
        listOf(V4ColorTokens.LightTextPrimary, V4ColorTokens.LightTextSecondary, V4ColorTokens.LightAccentText)
            .forEach { fg -> lightBackgrounds.forEach { bg -> assertReadable(fg, bg) } }
    }

    @Test
    fun `light primary is readable but brand fill remains baby blue`() {
        assertReadable(V4ColorTokens.LightAccentText, V4ColorTokens.LightSurface)
        assertReadable(V4ColorTokens.LightAccentText, V4ColorTokens.LightAccentContainer)
        assertReadable(V4ColorTokens.LightOnPrimary, V4ColorTokens.LightAccentText)
        assertReadable(V4ColorTokens.LightOnAccentContainer, V4ColorTokens.LightAccentContainer)
        assertReadable(V4ColorTokens.Accent, V4ColorTokens.AccentContainer)
        assertEquals(0xFF64BFF2, V4ColorTokens.Accent)
        assertEquals(0xFFDCEEFB, V4ColorTokens.LightAccentContainer)
        assertTrue("Brand blue is not a valid light-background text color",
            ratio(V4ColorTokens.Accent, V4ColorTokens.LightSurface) < 4.5)
    }

    @Test
    fun `publish brand onPrimary passes and white would fail`() {
        assertReadable(V4ColorTokens.OnAccent, V4ColorTokens.Accent)
        assertTrue(ratio(V4ColorTokens.LightOnPrimary, V4ColorTokens.Accent) < 3.0)
    }

    @Test
    fun `fallback is fully opaque while glass remains translucent`() {
        assertEquals(255L, V4ColorTokens.DarkSurfaceGlassFallback ushr 24)
        assertEquals(255L, V4ColorTokens.LightSurfaceGlassFallback ushr 24)
        assertTrue((V4ColorTokens.DarkSurfaceGlass ushr 24) in 1L..254L)
        assertTrue((V4ColorTokens.LightSurfaceGlass ushr 24) in 1L..254L)
    }

    private fun assertReadable(fg: Long, bg: Long) {
        assertEquals("Reading surfaces must be opaque after compositing", 255L, bg ushr 24)
        val contrast = ratio(fg, bg)
        assertTrue("${fg.toString(16)} on ${bg.toString(16)} is $contrast, expected >= 4.5", contrast >= 4.5)
    }

    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun ratio(fg: Long, bg: Long): Double {
        val l1 = luminance(fg)
        val l2 = luminance(bg)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun composite(fg: Long, bg: Long): Long {
        val alpha = ((fg ushr 24) and 0xFF) / 255.0
        var result = 0xFF000000L
        listOf(16, 8, 0).forEach { shift ->
            val f = ((fg ushr shift) and 0xFF).toInt()
            val b = ((bg ushr shift) and 0xFF).toInt()
            result = result or ((f * alpha + b * (1 - alpha)).roundToInt().toLong() shl shift)
        }
        return result
    }
}
