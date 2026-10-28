package com.nightrider.driver

/**
 * Animation of *accepted* GPS fixes for the map marker and follow camera.
 *
 * This changes display motion only: routing, Field Test CSV and quality gates
 * continue to use the original accepted/raw coordinates as appropriate.
 * No extrapolation is done after the latest measured position.
 */
class NavigationMotionSmoother {
    data class Frame(
        val position: SimulationPoint,
        val bearingDegrees: Double?,
        val progress: Double
    )

    private var start: Frame? = null
    private var destination: SimulationPoint? = null
    private var destinationBearing: Double? = null
    private var startedAtMs: Long = 0L
    private var durationMs: Long = 0L
    private var lastFixReceivedAtMs: Long? = null

    fun clear() {
        start = null
        destination = null
        destinationBearing = null
        durationMs = 0L
        lastFixReceivedAtMs = null
    }

    fun acceptFix(point: SimulationPoint, bearing: Double?, nowMs: Long) {
        require(point.lat.isFinite() && point.lon.isFinite())
        val previousFrame = frame(nowMs)
        val lastGap = lastFixReceivedAtMs?.let { (nowMs - it).coerceAtLeast(0L) }
        val normalizedBearing = bearing?.takeIf { it.isFinite() }?.let { wrap(it) }
        val oldPoint = previousFrame?.position
        val displacement = if (oldPoint != null) SimulationLeg.meters(oldPoint, point) else 0.0
        val interrupted = lastGap != null && lastGap > 12_000L
        // A 5s GPS cadence animates for ~3.5s, with a short static interval.
        // Never extrapolate past an actual GPS measurement or animate a
        // long recovery jump (which could hide a GPS outage or a missed turn).
        durationMs = if (oldPoint == null || interrupted || displacement < 2.0 ||
            displacement > 400.0) 0L
        else ((lastGap ?: 2_500L) * 0.70).toLong().coerceIn(500L, 3_600L)
        start = Frame(oldPoint ?: point,
            previousFrame?.bearingDegrees ?: normalizedBearing, 0.0)
        destination = point
        destinationBearing = normalizedBearing ?: previousFrame?.bearingDegrees
        startedAtMs = nowMs
        lastFixReceivedAtMs = nowMs
    }

    fun frame(nowMs: Long): Frame? {
        val origin = start ?: return null
        val endpoint = destination ?: return origin
        val fraction = if (durationMs <= 0L) 1.0
            else ((nowMs - startedAtMs).toDouble() / durationMs).coerceIn(0.0, 1.0)
        val bearing = if (origin.bearingDegrees != null && destinationBearing != null) {
            val delta = signedAngleDifference(origin.bearingDegrees, destinationBearing!!)
            wrap(origin.bearingDegrees + delta * fraction)
        } else destinationBearing ?: origin.bearingDegrees
        val lonDiff = signedAngleDifference(origin.position.lon, endpoint.lon)
        return Frame(
            SimulationPoint(
                origin.position.lat + (endpoint.lat - origin.position.lat) * fraction,
                wrapLongitude(origin.position.lon + lonDiff * fraction)
            ),
            bearing,
            fraction
        )
    }

    private fun signedAngleDifference(from: Double, to: Double): Double =
        ((to - from + 540.0) % 360.0) - 180.0

    private fun wrap(angle: Double): Double = ((angle % 360.0) + 360.0) % 360.0

    private fun wrapLongitude(lon: Double): Double = ((lon + 540.0) % 360.0) - 180.0
}
