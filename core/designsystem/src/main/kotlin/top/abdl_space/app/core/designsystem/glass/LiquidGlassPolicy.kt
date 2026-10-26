package top.abdl_space.app.core.designsystem.glass

/**
 * Pure eligibility check. No Android classes, process-global initialization, or RAM heuristics.
 * Unknown RAM, an unloaded transparency preference, and software rendering all fail closed.
 *
 * This is a capability gate, not proof that a GPU/driver has passed a rendering test.
 */
object LiquidGlassPolicy {
    const val MIN_GLASS_API: Int = 33

    fun canUseGlass(
        sdkInt: Int,
        isLowRamDevice: Boolean? = null,
        reduceTransparency: Boolean = true,
        isHardwareAccelerated: Boolean = false,
    ): Boolean = sdkInt >= MIN_GLASS_API &&
        isLowRamDevice == false &&
        !reduceTransparency &&
        isHardwareAccelerated
}
