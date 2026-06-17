package btcrenaud.advancedmenus.util

import com.typewritermc.engine.paper.utils.Color

/**
 * Converts a [Color] to its ARGB integer representation.
 */
fun Color.toARGB(alphaOverride: Double? = null): Int {
    val a = if (alphaOverride != null) (alphaOverride.coerceIn(0.0, 1.0) * 255).toInt() else alpha
    return (a shl 24) or (red shl 16) or (green shl 8) or blue
}
