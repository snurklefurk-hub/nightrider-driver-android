package com.nightrider.driver

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Experimental heading-only inertial assistance for *opt-in Field Test*.
 *
 * Accurate GPS fixes remain the sole absolute-position source. A phone fixed
 * to the car can help estimate heading between GPS updates by projecting the
 * gyroscope's angular velocity onto the measured gravity (vertical) axis.
 * Any phone movement relative to the car breaks that assumption.
 *
 * The orientation vector is diagnostic only: magnetic interference and phone
 * mounting offset make raw phone azimuth unsafe as vehicle direction.
 */
data class FieldMotionSnapshot(
    val headingDeg: Double?,
    val yawRateDps: Double?,
    val linearAccelerationMps2: Double?,
    val phoneAzimuthDeg: Double?,
    val gpsAnchorAgeS: Double?
)

class FieldMotionFusion {
    private var gravity: DoubleArray? = null
    private var gravityFromSensor = false
    private var heading: Double? = null
    private var lastGpsAnchorNs: Long? = null
    private var lastGyroNs: Long? = null
    private var lastGyroSeenNs: Long? = null
    private var lastMotionNs: Long? = null
    private var yawRateDps: Double? = null
    private var linearAccelerationMps2: Double? = null
    private var phoneAzimuthDeg: Double? = null
    private var vehicleMoving = false

    fun reset() {
        gravity = null
        gravityFromSensor = false
        heading = null
        lastGpsAnchorNs = null
        lastGyroNs = null
        lastGyroSeenNs = null
        lastMotionNs = null
        yawRateDps = null
        linearAccelerationMps2 = null
        phoneAzimuthDeg = null
        vehicleMoving = false
    }

    fun gravity(x: Double, y: Double, z: Double) {
        if (!validGravity(x, y, z)) return
        gravity = doubleArrayOf(x, y, z)
        gravityFromSensor = true
    }

    fun accelerometer(x: Double, y: Double, z: Double, timeNs: Long) {
        if (!listOf(x, y, z).all(Double::isFinite)) return
        // Low-pass accelerometer is a fallback when Android gravity is absent.
        if (!gravityFromSensor) {
            val previous = gravity
            gravity = if (previous == null) doubleArrayOf(x, y, z) else doubleArrayOf(
                previous[0] * 0.94 + x * 0.06,
                previous[1] * 0.94 + y * 0.06,
                previous[2] * 0.94 + z * 0.06
            )
        }
        val g = gravity ?: return
        val magnitude = sqrt((x - g[0]) * (x - g[0]) +
            (y - g[1]) * (y - g[1]) +
            (z - g[2]) * (z - g[2]))
        if (magnitude.isFinite()) {
            linearAccelerationMps2 = magnitude.coerceIn(0.0, 60.0)
            lastMotionNs = timeNs
        }
    }

    fun phoneAzimuth(azimuthDeg: Double) {
        if (azimuthDeg.isFinite()) phoneAzimuthDeg = normalize(azimuthDeg)
    }

    /** GPS course is authoritative only when speed is high enough for bearing. */
    fun gpsCourse(bearingDeg: Double?, speedMps: Double?, timeNs: Long) {
        vehicleMoving = speedMps != null && speedMps.isFinite() && speedMps >= 1.5
        if (bearingDeg == null || !bearingDeg.isFinite() ||
            speedMps == null || !speedMps.isFinite() || speedMps < 3.0) return
        val gps = normalize(bearingDeg)
        val existing = heading
        heading = if (existing == null ||
            lastGpsAnchorNs == null || timeNs - lastGpsAnchorNs!! > 8_000_000_000L) gps
        else {
            val error = delta(existing, gps)
            if (abs(error) > 45.0) gps else normalize(existing + error * 0.75)
        }
        lastGpsAnchorNs = timeNs
    }

    fun gyroscope(xRadS: Double, yRadS: Double, zRadS: Double, timeNs: Long) {
        if (!listOf(xRadS, yRadS, zRadS).all(Double::isFinite)) return
        val previous = lastGyroNs
        lastGyroNs = timeNs
        lastGyroSeenNs = timeNs
        val g = gravity ?: run { yawRateDps = null; return }
        val norm = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2])
        if (norm !in 7.0..12.0) { yawRateDps = null; return }
        // Gravity points down; clockwise automotive yaw is positive. The
        // signed dot product remains correct in portrait or landscape mounts.
        val signedRate = (xRadS * g[0] + yRadS * g[1] + zRadS * g[2]) *
            (180.0 / Math.PI) / norm
        if (!signedRate.isFinite()) return
        val boundedRate = signedRate.coerceIn(-120.0, 120.0)
        yawRateDps = if (abs(boundedRate) < 0.7) 0.0 else boundedRate

        val anchorNs = lastGpsAnchorNs ?: return
        if (!vehicleMoving || heading == null || timeNs < anchorNs ||
            timeNs - anchorNs > 8_000_000_000L || previous == null) return
        val deltaS = (timeNs - previous) / 1_000_000_000.0
        if (deltaS <= 0.0 || deltaS > 0.25) return
        heading = normalize(heading!! + yawRateDps!! * deltaS)
    }

    /** Never claim a fused heading without recent GPS calibration and gyro. */
    fun headingAt(timeNs: Long): Double? {
        val anchored = lastGpsAnchorNs ?: return null
        val gyro = lastGyroSeenNs ?: return null
        if (!vehicleMoving || timeNs < anchored || timeNs - anchored > 8_000_000_000L ||
            timeNs < gyro || timeNs - gyro > 1_000_000_000L) return null
        return heading
    }

    fun snapshot(timeNs: Long): FieldMotionSnapshot {
        val age = lastGpsAnchorNs?.let {
            if (timeNs >= it) (timeNs - it) / 1_000_000_000.0 else null
        }
        return FieldMotionSnapshot(
            headingAt(timeNs),
            yawRateDps?.takeIf {
                lastGyroSeenNs?.let { ns -> timeNs >= ns && timeNs - ns < 2_000_000_000L } == true
            },
            linearAccelerationMps2?.takeIf {
                lastMotionNs?.let { ns -> timeNs >= ns && timeNs - ns < 2_000_000_000L } == true
            },
            phoneAzimuthDeg,
            age
        )
    }

    private fun validGravity(x: Double, y: Double, z: Double): Boolean =
        listOf(x, y, z).all(Double::isFinite) &&
            sqrt(x * x + y * y + z * z) in 7.0..12.0

    private fun normalize(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0
    private fun delta(from: Double, to: Double): Double = ((to - from + 540.0) % 360.0) - 180.0
}
