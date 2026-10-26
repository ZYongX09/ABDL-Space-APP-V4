package top.abdl_space.app.core.designsystem.glass

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassPolicyTest {
    @Test
    fun `all inputs must be explicitly safe to opt in`() {
        assertFalse(LiquidGlassPolicy.canUseGlass(sdkInt = 33))
        assertFalse(LiquidGlassPolicy.canUseGlass(sdkInt = 37))
        assertTrue(eligible(sdk = 33))
        assertTrue(eligible(sdk = 37))
    }

    @Test
    fun `pre 33 never enables even when every other input opts in`() {
        listOf(Int.MIN_VALUE, 0, 26, 28, 30, 31, 32).forEach { sdk ->
            assertFalse("SDK $sdk must not load miuix-blur", eligible(sdk))
        }
    }

    @Test
    fun `low or unknown ram always falls back`() {
        listOf(33, 34, 37).forEach { sdk ->
            assertFalse(eligible(sdk, lowRam = true))
            assertFalse(eligible(sdk, lowRam = null))
        }
    }

    @Test
    fun `unloaded transparency preference and unknown hardware default to disabled`() {
        assertFalse(LiquidGlassPolicy.canUseGlass(33, isLowRamDevice = false))
        assertFalse(LiquidGlassPolicy.canUseGlass(33, isLowRamDevice = false, reduceTransparency = false))
        assertFalse(LiquidGlassPolicy.canUseGlass(33, isLowRamDevice = false, isHardwareAccelerated = true))
    }

    @Test
    fun `changing accessibility and hardware inputs takes effect immediately without global init`() {
        assertTrue(eligible(33))
        assertFalse(eligible(33, reduce = true))
        assertFalse(eligible(33, hardware = false))
        assertTrue(eligible(33))
        assertFalse(eligible(33, lowRam = true))
        assertTrue(eligible(33))
    }

    private fun eligible(
        sdk: Int,
        lowRam: Boolean? = false,
        reduce: Boolean = false,
        hardware: Boolean = true,
    ) = LiquidGlassPolicy.canUseGlass(sdk, lowRam, reduce, hardware)
}
