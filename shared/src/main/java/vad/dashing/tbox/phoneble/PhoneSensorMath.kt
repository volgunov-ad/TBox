package vad.dashing.tbox.phoneble

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure sensor math for phone gyroscope projection onto gravity (`TYPE_GYROSCOPE` + `TYPE_GRAVITY` /
 * `TYPE_ACCELEROMETER`) and tilt-compensated magnetometer azimuth (`TYPE_MAGNETIC_FIELD`).
 */
object PhoneSensorMath {
    const val STANDARD_GRAVITY_MPS2 = 9.80665f
    const val MIN_VALID_GRAVITY_MPS2 = 6.0f
    const val MAX_VALID_GRAVITY_MPS2 = 14.0f

    /** Max gravity-vector rotation within one 100–200 ms window before marking phone as in-hand. */
    const val MAX_WINDOW_GRAVITY_TILT_DEG = 6.0f

    /** Gravity-vector shift from the settled holder orientation that triggers a mount-offset reset. */
    const val MOUNT_MOVED_GRAVITY_TILT_DEG = 10.0f

    /** Valid Earth magnetic field magnitude range (µT) inside a car without a magnetic mount. */
    const val MIN_VALID_MAG_FIELD_UT = 25.0f
    const val MAX_VALID_MAG_FIELD_UT = 65.0f

    data class Vec3(val x: Float, val y: Float, val z: Float) {
        fun norm(): Float = sqrt(x * x + y * y + z * z)

        fun normalized(): Vec3? {
            val n = norm()
            if (!n.isFinite() || n < 1e-4f) return null
            return Vec3(x / n, y / n, z / n)
        }

        fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

        fun cross(other: Vec3): Vec3 = Vec3(
            x = y * other.z - z * other.y,
            y = z * other.x - x * other.z,
            z = x * other.y - y * other.x,
        )
    }

    /**
     * Projects 3D angular velocity (rad/s) onto the upward unit gravity vector:
     * `ω_vert = ω · ĝ`, returned in degrees/s (counter-clockwise / left turn +).
     * Returns null when gravity magnitude is implausible (free fall / strong impact).
     */
    fun projectYawRateDegPerSec(
        gyroRadPerSec: Vec3,
        gravityMps2: Vec3,
    ): Float? {
        val gNorm = gravityMps2.norm()
        if (!gNorm.isFinite() || gNorm !in MIN_VALID_GRAVITY_MPS2..MAX_VALID_GRAVITY_MPS2) {
            return null
        }
        val gHat = gravityMps2.normalized() ?: return null
        val omegaVertRad = gyroRadPerSec.dot(gHat)
        if (!omegaVertRad.isFinite()) return null
        return Math.toDegrees(omegaVertRad.toDouble()).toFloat()
    }

    /** Angle (°) between two 3D vectors; returns 180° if either vector is degenerate. */
    fun angleBetweenDeg(a: Vec3, b: Vec3): Float {
        val ua = a.normalized() ?: return 180f
        val ub = b.normalized() ?: return 180f
        val cosTheta = ua.dot(ub).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosTheta.toDouble())).toFloat()
    }

    /** Equivalent chord change in milli-g (`1000 × |ĝ_1 - ĝ_0|`) for compact telemetry packing. */
    fun gravityDeltaMilliG(a: Vec3, b: Vec3): Int {
        val ua = a.normalized() ?: return 1000
        val ub = b.normalized() ?: return 1000
        val dx = ua.x - ub.x
        val dy = ua.y - ub.y
        val dz = ua.z - ub.z
        val d = sqrt(dx * dx + dy * dy + dz * dz)
        return (d * 1000f).roundToInt().coerceIn(0, 65534)
    }

    data class TiltCompensatedMag(
        val azimuthDeg: Float,
        val fieldStrengthUt: Float,
        val pitchDeg: Int,
        val rollDeg: Int,
    )

    /**
     * Computes tilt-compensated magnetic azimuth (0..360°) from `TYPE_MAGNETIC_FIELD` (µT)
     * and gravity vector (`TYPE_GRAVITY` or low-pass `TYPE_ACCELEROMETER`), without using
     * `TYPE_ROTATION_VECTOR` (which mixes gyroscope).
     */
    fun computeTiltCompensatedMag(
        magUt: Vec3,
        gravityMps2: Vec3,
    ): TiltCompensatedMag? {
        val fieldStrength = magUt.norm()
        if (!fieldStrength.isFinite() || fieldStrength < 1e-3f) return null
        val gHat = gravityMps2.normalized() ?: return null
        val east = magUt.cross(gHat).normalized() ?: return null
        val north = gHat.cross(east).normalized() ?: return null

        // Device +Y axis projected onto horizontal (East, North) plane:
        // azimuth = atan2(east.y, north.y) in [0, 360)
        val azRad = atan2(east.y.toDouble(), north.y.toDouble())
        var azDeg = Math.toDegrees(azRad).toFloat()
        if (azDeg < 0f) azDeg += 360f

        val pitchDeg = Math.toDegrees(asin((-gHat.y).coerceIn(-1f, 1f).toDouble()))
            .roundToInt()
            .coerceIn(-90, 90)
        val rollDeg = Math.toDegrees(atan2(-gHat.x.toDouble(), gHat.z.toDouble()))
            .roundToInt()
            .coerceIn(-127, 127)

        return TiltCompensatedMag(
            azimuthDeg = azDeg % 360f,
            fieldStrengthUt = fieldStrength,
            pitchDeg = pitchDeg,
            rollDeg = rollDeg,
        )
    }

    fun isMagSampleUsable(
        accuracy: Int,
        fieldStrengthUt: Float,
        mountStable: Boolean,
    ): Boolean {
        if (!mountStable) return false
        if (accuracy < 2) return false
        if (!fieldStrengthUt.isFinite()) return false
        return fieldStrengthUt in MIN_VALID_MAG_FIELD_UT..MAX_VALID_MAG_FIELD_UT
    }
}
