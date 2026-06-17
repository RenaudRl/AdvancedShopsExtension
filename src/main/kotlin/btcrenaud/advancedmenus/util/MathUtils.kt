package btcrenaud.advancedmenus.util

/**
 * Linear interpolation between two values.
 */
fun lerp(a: Double, b: Double, f: Double): Double {
    return a + f * (b - a)
}

/**
 * Linear interpolation between two floats.
 */
fun lerp(a: Float, b: Float, f: Float): Float {
    return a + f * (b - a)
}
