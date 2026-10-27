package com.nightrider.driver

import kotlin.math.*

/** In-memory road-test samples. Never touches disk or a network service. */
data class FieldFix(
    val epochMs: Long,
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float
)

class FieldTrace(private val limit: Int = 1500) {
    init { require(limit >= 2) }
    private val recorded = ArrayList<FieldFix>()
    var recording: Boolean = false
        private set

    val samples: List<FieldFix> get() = recorded.toList()

    fun start() { recorded.clear(); recording = true }
    fun stop() { recording = false }
    fun clear() { recording = false; recorded.clear() }

    fun add(fix: FieldFix): Boolean {
        if (!recording || fix.epochMs <= 0L || !fix.lat.isFinite() || !fix.lon.isFinite() ||
            fix.lat !in -90.0..90.0 || fix.lon !in -180.0..180.0 ||
            !fix.accuracyMeters.isFinite() || fix.accuracyMeters < 0f ||
            !fix.speedMetersPerSecond.isFinite()) return false
        val last = recorded.lastOrNull()
        // Avoid duplicate network/GPS updates and excessive samples.
        if (last != null && fix.epochMs - last.epochMs < 1000L) return false
        if (recorded.size == limit) recorded.removeAt(0)
        recorded.add(fix)
        return true
    }

    fun distanceMeters(): Double {
        var meters = 0.0
        for (i in 1 until recorded.size) {
            val a = recorded[i - 1]; val b = recorded[i]
            // Skip low-quality jumps in the summary; keep raw samples for review.
            if (a.accuracyMeters > 100f || b.accuracyMeters > 100f) continue
            val delta = SimulationLeg.meters(
                SimulationPoint(a.lat, a.lon), SimulationPoint(b.lat, b.lon))
            val seconds = (b.epochMs - a.epochMs) / 1000.0
            if (delta <= max(50.0, seconds * 55.0)) meters += delta
        }
        return meters
    }

    fun csv(): String = buildString {
        append("epoch_ms,latitude,longitude,accuracy_m,speed_mps\n")
        for (s in recorded) {
            append(s.epochMs).append(',').append(s.lat).append(',').append(s.lon)
                .append(',').append(s.accuracyMeters).append(',')
                .append(s.speedMetersPerSecond).append('\n')
        }
    }
}

/** Smallest horizontal separation from a GPS fix to an existing planned route. */
object FieldRouteMath {
    fun distanceMeters(point: SimulationPoint, route: List<SimulationPoint>): Double? {
        if (route.isEmpty()) return null
        val metersPerLon = 111195.0 * cos(Math.toRadians(point.lat))
        fun xy(p: SimulationPoint) =
            Pair((p.lon - point.lon) * metersPerLon, (p.lat - point.lat) * 111195.0)
        var closest = Double.POSITIVE_INFINITY
        for (i in route.indices) {
            val (ax, ay) = xy(route[i])
            if (i == route.lastIndex) {
                closest = min(closest, hypot(ax, ay))
                continue
            }
            val (bx, by) = xy(route[i + 1])
            val dx = bx - ax; val dy = by - ay
            val t = if (dx * dx + dy * dy > 0) ((-ax * dx - ay * dy) /
                (dx * dx + dy * dy)).coerceIn(0.0, 1.0) else 0.0
            closest = min(closest, hypot(ax + t * dx, ay + t * dy))
        }
        return closest
    }
}
