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
