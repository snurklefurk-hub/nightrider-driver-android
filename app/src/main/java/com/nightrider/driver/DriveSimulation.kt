package com.nightrider.driver

import kotlin.math.*

/** Deterministic, device-independent playback. No GPS, account, API or clock access. */
data class SimulationPoint(val lat: Double, val lon: Double)

class SimulationLeg(val points: List<SimulationPoint>) {
    init {
        require(points.size >= 2)
        require(points.all { it.lat.isFinite() && it.lon.isFinite() && it.lat in -90.0..90.0 && it.lon in -180.0..180.0 })
    }
    private val distances = DoubleArray(points.size).also { values ->
        for (i in 1 until points.size) values[i] = values[i - 1] + meters(points[i - 1], points[i])
    }
    val lengthMeters: Double get() = distances.last()

    fun pointAt(distance: Double): SimulationPoint {
        val d = distance.coerceIn(0.0, lengthMeters)
        if (d >= lengthMeters) return points.last()
        var i = distances.binarySearch(d)
        if (i >= 0) return points[i]
        i = (-i - 1).coerceIn(1, points.lastIndex)
        val span = distances[i] - distances[i - 1]
        val fraction = if (span > 0.0) (d - distances[i - 1]) / span else 0.0
        val a = points[i - 1]; val b = points[i]
        return SimulationPoint(a.lat + (b.lat - a.lat) * fraction, a.lon + (b.lon - a.lon) * fraction)
    }

    companion object {
        fun meters(a: SimulationPoint, b: SimulationPoint): Double {
            val dLat = Math.toRadians(b.lat - a.lat); val dLon = Math.toRadians(b.lon - a.lon)
            val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
            return 6371000.0 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }
    }
}

enum class SimulationStage(val rideStatus: String, val label: String) {
    ASSIGNED("accepted", "Assigned"), PICKUP("to_pickup", "Heading to pickup"),
    WAITING("at_pickup", "At pickup"), TRIP("to_dropoff", "Heading to destination"),
    ARRIVED("at_dropoff", "At destination"), COMPLETE("completed", "Completed")
}

enum class SimulationGps { NORMAL, LOST, OFF_ROUTE }

class DriveSimulation(val approach: SimulationLeg, val trip: SimulationLeg) {
    var stage = SimulationStage.ASSIGNED
        private set
    var distanceMeters = 0.0
        private set
    var playing = false
        private set
    var speedMultiplier = 1.0
    var gps = SimulationGps.NORMAL
    val leg: SimulationLeg get() = if (stage in listOf(SimulationStage.TRIP, SimulationStage.ARRIVED, SimulationStage.COMPLETE)) trip else approach
    val progress: Double get() = if (leg.lengthMeters > 0) distanceMeters / leg.lengthMeters else 1.0
    val position: SimulationPoint? get() {
        if (gps == SimulationGps.LOST) return null
        val point = leg.pointAt(distanceMeters)
        // Fixed, repeatable lateral displacement of roughly 130 metres.
        return if (gps == SimulationGps.OFF_ROUTE) point.copy(lat = point.lat + 0.0012) else point
    }

    fun setStage(value: SimulationStage) {
        playing = false; stage = value
        distanceMeters = if (value in listOf(SimulationStage.WAITING, SimulationStage.ARRIVED, SimulationStage.COMPLETE)) leg.lengthMeters else 0.0
    }
    fun seek(fraction: Double) {
        if (!fraction.isFinite() || stage in listOf(SimulationStage.WAITING, SimulationStage.ARRIVED, SimulationStage.COMPLETE)) return
        if (stage == SimulationStage.ASSIGNED && fraction > 0.0) setStage(SimulationStage.PICKUP)
        distanceMeters = leg.lengthMeters * fraction.coerceIn(0.0, 1.0)
    }
    fun pause() { playing = false }
    fun play() {
        when (stage) {
            SimulationStage.ASSIGNED -> setStage(SimulationStage.PICKUP)
            SimulationStage.WAITING -> setStage(SimulationStage.TRIP)
            SimulationStage.ARRIVED, SimulationStage.COMPLETE -> return
            else -> {}
        }
        playing = true
    }
    fun reset() { gps = SimulationGps.NORMAL; setStage(SimulationStage.ASSIGNED) }
    fun advance(realSeconds: Double) {
        if (!playing || realSeconds <= 0.0 || !realSeconds.isFinite()) return
        if (stage !in listOf(SimulationStage.PICKUP, SimulationStage.TRIP)) { pause(); return }
        // 30 mph at 1x. Simulated movement uses elapsed time, never timer count.
        distanceMeters = min(leg.lengthMeters, distanceMeters + 13.4112 * speedMultiplier.coerceIn(1.0, 30.0) * realSeconds)
        if (distanceMeters >= leg.lengthMeters) {
            setStage(if (stage == SimulationStage.PICKUP) SimulationStage.WAITING else SimulationStage.ARRIVED)
        }
    }
}
