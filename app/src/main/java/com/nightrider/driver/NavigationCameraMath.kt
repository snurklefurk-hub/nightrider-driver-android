package com.nightrider.driver

import kotlin.math.*

/** Flat, forward-facing camera helpers. No service calls or location collection. */
object NavigationCameraMath {
    fun zoomForSpeed(speedMps: Double): Double = when {
        !speedMps.isFinite() || speedMps < 0 -> 15.7
        speedMps < 4.0 -> 16.8   // stopped or navigating a parking lot
        speedMps < 13.5 -> 16.0  // local streets
        speedMps < 24.0 -> 15.2  // faster roads
        else -> 14.5            // highway overview
    }


    /**
     * Look ahead for a significant bend in the planned route without treating
     * geometry as turn-by-turn directions. Returns null if GPS is far off route.
     * This hint is only used for camera scale; it never announces a maneuver.
     */
    fun nextBendMeters(driver: SimulationPoint, route: List<SimulationPoint>): Double? {
        if (route.size < 3 || route.size > 30000) return null
        val metersPerDegree = 111195.0
        val longitudeScale = metersPerDegree * cos(Math.toRadians(driver.lat))
        var bestOffsetSq = Double.POSITIVE_INFINITY
        var nearestSegment = -1
        var nearestFraction = 0.0
        for (i in 0 until route.lastIndex) {
            val a = route[i]
            val b = route[i + 1]
            val ax = (a.lon - driver.lon) * longitudeScale
            val ay = (a.lat - driver.lat) * metersPerDegree
            val dx = (b.lon - a.lon) * longitudeScale
            val dy = (b.lat - a.lat) * metersPerDegree
            val denominator = dx * dx + dy * dy
            if (denominator < 0.01) continue
            val fraction = (-(ax * dx + ay * dy) / denominator).coerceIn(0.0, 1.0)
            val x = ax + fraction * dx
            val y = ay + fraction * dy
            val offset = x * x + y * y
            if (offset < bestOffsetSq) {
                bestOffsetSq = offset
                nearestSegment = i
                nearestFraction = fraction
            }
        }
        if (nearestSegment < 0 || bestOffsetSq > 70.0 * 70.0) return null
        var forward = SimulationLeg.meters(
            route[nearestSegment], route[nearestSegment + 1]
        ) * (1.0 - nearestFraction)
        for (i in nearestSegment + 1 until route.lastIndex) {
            if (forward > 240.0) break
            val before = SimulationLeg.meters(route[i - 1], route[i])
            val after = SimulationLeg.meters(route[i], route[i + 1])
            if (before >= 10.0 && after >= 10.0) {
                val incoming = bearingDegrees(route[i - 1], route[i])
                val outgoing = bearingDegrees(route[i], route[i + 1])
                if (incoming != null && outgoing != null) {
                    val delta = abs(((outgoing - incoming + 540.0) % 360.0) - 180.0)
                    if (delta >= 48.0) return forward
                }
            }
            forward += after
        }
        return null
    }

    /** Ease in at upcoming sharp geometry, but never claim a real turn instruction. */
    fun zoomForUpcomingBend(base: Double, bendMeters: Double?): Double {
        if (bendMeters == null || !bendMeters.isFinite() || bendMeters < 0) return base
        val minimum = when {
            bendMeters <= 55.0 -> 16.7
            bendMeters <= 110.0 -> 16.1
            bendMeters <= 180.0 -> 15.6
            else -> base
        }
        return max(base, minimum)
    }

    /** Restrict changes between GPS updates to keep camera transitions calm. */
    fun easeZoom(previous: Double?, desired: Double): Double {
        if (previous == null || !previous.isFinite()) return desired
        return desired.coerceIn(previous - 0.55, previous + 0.55)
    }

    fun bearingDegrees(from: SimulationPoint, to: SimulationPoint): Double? {
        if (SimulationLeg.meters(from, to) < 3.0) return null
        val start = Math.toRadians(from.lat)
        val end = Math.toRadians(to.lat)
        val delta = Math.toRadians(to.lon - from.lon)
        val y = sin(delta) * cos(end)
        val x = cos(start) * sin(end) - sin(start) * cos(end) * cos(delta)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Position the vehicle below the center to reveal more road ahead. */
    fun lookAhead(point: SimulationPoint, bearingDegrees: Double?, speedMps: Double): SimulationPoint {
        if (bearingDegrees == null || !bearingDegrees.isFinite()) return point
        val aheadMeters = (65.0 + speedMps.coerceIn(0.0, 35.0) * 5.0).coerceAtMost(225.0)
        val angular = aheadMeters / 6371000.0
        val heading = Math.toRadians(bearingDegrees)
        val lat = Math.toRadians(point.lat)
        val lon = Math.toRadians(point.lon)
        val nextLat = asin(sin(lat) * cos(angular) + cos(lat) * sin(angular) * cos(heading))
        val nextLon = lon + atan2(
            sin(heading) * sin(angular) * cos(lat),
            cos(angular) - sin(lat) * sin(nextLat)
        )
        return SimulationPoint(Math.toDegrees(nextLat), ((Math.toDegrees(nextLon) + 540) % 360) - 180)
    }
}
