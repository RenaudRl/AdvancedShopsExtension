package btcrenaud.advancedmenus.util

import org.bukkit.Location
import org.bukkit.util.Vector

/**
 * Camera-space basis vectors and projection utilities for the Advanced Menu system.
 *
 * Coordinate conventions (Minecraft):
 *   - Yaw 0   = South (+Z), increases clockwise (viewed from above)
 *   - Yaw 90  = West  (-X)
 *   - Yaw -90 = East  (+X)
 *   - Pitch -90 = straight UP,  Pitch +90 = straight DOWN
 *
 * Anchor-space (0-100) maps to the full visible player screen:
 *   - X: 0 = left edge, 50 = center, 100 = right edge
 *   - Y: 0 = top  edge, 50 = center, 100 = bottom edge
 *
 * Because Minecraft's FOV setting is the *vertical* FOV, horizontal extent is
 * wider by the screen aspect ratio (16∶9 → factor 16/9). The methods
 * [anchorToWorld] and [cursorToAnchor] account for this so that anchor (0,0)
 * truly corresponds to the top-left corner of the player's screen.
 */
class CameraBasis(yaw: Float, pitch: Float) {

    private val spawnYaw: Float  = yaw
    private val spawnPitch: Float = pitch

    val forward: Vector
    val right: Vector
    val up: Vector

    init {
        // Match the reference implementation (AdvancedMenu/Aurus CameraBasis.java):
        //   forward = (-sinY*cosP, -sinP, cosY*cosP)  normalised
        //   right   = (-fz, 0, fx)                    normalised (horizontal only)
        //   up      = right × forward                  normalised
        val yRad = Math.toRadians(yaw.toDouble())
        val pRad = Math.toRadians(pitch.toDouble())

        val sinY = Math.sin(yRad);  val cosY = Math.cos(yRad)
        val sinP = Math.sin(pRad);  val cosP = Math.cos(pRad)

        val fx = -sinY * cosP;  val fy = -sinP;  val fz = cosY * cosP
        val fLen = Math.sqrt(fx * fx + fy * fy + fz * fz)
        forward = Vector(fx / fLen, fy / fLen, fz / fLen)

        val rxr = -forward.z;  val rzr = forward.x
        val rLen = Math.sqrt(rxr * rxr + rzr * rzr)
        right = Vector(rxr / rLen, 0.0, rzr / rLen)

        val ux = right.y * forward.z - right.z * forward.y
        val uy = right.z * forward.x - right.x * forward.z
        val uz = right.x * forward.y - right.y * forward.x
        val uLen = Math.sqrt(ux * ux + uy * uy + uz * uz)
        up = Vector(ux / uLen, uy / uLen, uz / uLen)
    }

    // ── World-space projection ───────────────────────────────────────────

    /**
     * Projects a 2D screen offset (right-axis, up-axis) into a 3D world location.
     *
     * @param origin    Camera eye position
     * @param x         Offset along the [right] axis (blocks)
     * @param y         Offset along the [up]    axis (blocks)
     * @param distance  Forward distance to the screen plane (blocks)
     */
    fun screenToWorld(origin: Location, x: Double, y: Double, distance: Double): Location {
        return origin.clone()
            .add(forward.clone().multiply(distance))
            .add(right.clone().multiply(x))
            .add(up.clone().multiply(y))
    }

    // ── Cursor projection ────────────────────────────────────────────────

    /**
     * Projects the player's current head orientation onto the screen plane via
     * tangential (perspective-correct) projection.
     *
     * Algorithm (mirrors AdvancedMenu/Aurus CameraBasis.java §getCursorXY):
     *   - Δyaw   = playerYaw   − spawnYaw   (normalised to [-180, 180])
     *   - Δpitch = playerPitch − spawnPitch (normalised to [-180, 180])
     *   - screenX =  tan(Δyaw)   × distance
     *   - screenY = -tan(Δpitch) × distance   (negated: pitch↑ → cursor↑)
     *
     * @return World-space (right, up) offset in blocks from screen centre.
     *         Pass to [cursorToAnchor] to obtain anchor-space (0-100).
     */
    fun getCursorScreen(
        yaw: Float,
        pitch: Float,
        spawnYaw: Float,
        spawnPitch: Float,
        distance: Double
    ): Vec2 {
        var dYaw = (yaw - spawnYaw).toDouble()
        while (dYaw >  180.0) dYaw -= 360.0
        while (dYaw < -180.0) dYaw += 360.0

        var dPitch = (pitch - spawnPitch).toDouble()
        while (dPitch >  180.0) dPitch -= 360.0
        while (dPitch < -180.0) dPitch += 360.0

        // +tan(dYaw)   → looking right (positive yaw) moves cursor RIGHT ✓
        // -tan(dPitch) → looking up (negative pitch delta) moves cursor UP ✓
        val screenX =  Math.tan(Math.toRadians(dYaw))   * distance
        val screenY = -Math.tan(Math.toRadians(dPitch))  * distance

        return Vec2(screenX, screenY)
    }

    companion object {

        private const val BASE_FOV = 70.0

        /**
         * Parameters for 3D projection calibration.
         */
        data class CalibrationParams(
            val baseFov: Double = 70.0,
            val baseAspect: Double = 2.08,
            val correctionFactor: Double = 0.35
        )

        private val DEFAULT_CALIBRATION = CalibrationParams()

        /**
         * Calculates an effective FOV based on user feedback to handle Minecraft's 
         * perspective distortion. A +4.0 bias is applied for FOVs above 70.
         */
        fun getEffectiveFov(playerFov: Double): Double {
            // Pure mathematical projection: use the actual FOV.
            // Previous +4.0 biases were workarounds for a distance clamp that is now resolved.
            return playerFov
        }

        /**
         * Calculates a calibrated aspect ratio for a given FOV.
         * As FOV increases, the effective aspect ratio of Minecraft's projection
         * tends to stretch objects at the edges. This formula contracts the menu
         * horizontally to compensate.
         */
        fun getCalibratedAspect(actualFov: Double, params: CalibrationParams = DEFAULT_CALIBRATION): Double {
            // Pure mathematical projection: actual aspect ratio remains constant.
            // Previous power-curve shrinkages were workarounds for distance clamp logic.
            return params.baseAspect
        }

        /**
         * Adjusts a target distance to maintain consistent visual size across FOV settings
         * using perspective-correct tangent scaling.
         *
         * Formula: d_adj = d_ref * (tan(fov_ref/2) / tan(fov_actual/2))
         */
        fun adjustDistanceForFov(distance: Double, actualFov: Double): Double {
            return distance * (actualFov / 70.0)
        }

        /**
         * Converts a raw world-space cursor offset (output of [getCursorScreen]) to
         * anchor-space (0–100, X and Y independently).
         *
         * The horizontal extent is scaled by [ASPECT_RATIO] so that the full 0–100
         * anchor range maps to the full width of a 16∶9 screen, independent of whether
         * the player is in widescreen or not.
         *
         * @param x   World offset along the right axis   (blocks)
         * @param y   World offset along the up   axis   (blocks)
         * @param fov Vertical FOV in degrees
         * @param distance Forward distance to the screen plane (blocks)
         */
        fun cursorToAnchor(
            x: Double,
            y: Double,
            fov: Double,
            distance: Double,
            calibration: CalibrationParams = DEFAULT_CALIBRATION
        ): Vec2 {
            val halfH = distance * Math.tan(Math.toRadians(fov / 2.0))
            val halfW = halfH * 1.7777777777777777

            val nx = (x / (2.0 * halfW)) * 100.0 + 50.0
            val ny = 50.0 - (y / (2.0 * halfH)) * 100.0
            return Vec2(nx, ny)
        }

        /**
         * Converts anchor-space coordinates (0–100) back to world-space offsets
         * on the screen plane, accounting for screen aspect ratio.
         *
         * Anchor convention:
         *   X: 0 = left, 50 = center, 100 = right
         *   Y: 0 = top,  50 = center, 100 = bottom  (Y is inverted relative to world-up)
         *
         * @param ax   Anchor X (0–100)
         * @param ay   Anchor Y (0–100)
         * @param ox   Additive offset in anchor-percentage along X
         * @param oy   Additive offset in anchor-percentage along Y
         * @param fov  Vertical FOV in degrees
         * @param distance Forward distance to the screen plane (blocks)
         * @return (worldX, worldY) offsets along the right/up basis axes (blocks)
         */
        fun anchorToWorld(
            ax: Double, ay: Double,
            ox: Double, oy: Double,
            fov: Double, distance: Double,
            calibration: CalibrationParams = DEFAULT_CALIBRATION
        ): Vec2 {
            val halfH = distance * Math.tan(Math.toRadians(fov / 2.0))
            val halfW = halfH * 1.7777777777777777

            val tx = ax + ox
            val ty = ay + oy

            val x = ((tx - 50.0) / 100.0) * (2.0 * halfW)
            val y = ((50.0 - ty) / 100.0) * (2.0 * halfH)
            return Vec2(x, y)
        }
    }
}
