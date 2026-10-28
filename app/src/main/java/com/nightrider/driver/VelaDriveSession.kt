package com.nightrider.driver

import app.vela.core.model.LatLng
import app.vela.core.model.Route
import app.vela.core.model.TravelMode
import app.vela.core.nav.NavEngine
import app.vela.core.nav.NavEvent
import app.vela.core.nav.NavState

/** NightRider's silent boundary around Vela's pure navigation engine. No ride mutations. */
class VelaDriveSession {
    data class Fix(val point: LatLng, val elapsedMs: Long, val accuracyM: Double,
                   val speedMps: Double?, val bearingDeg: Double?)
    var route: Route? = null
        private set
    var state = NavState()
        private set
    var lastFix: Fix? = null
        private set
    var heading = 0.0
        private set

    fun clearRoute() { route = null; state = NavState() }
    fun adopt(value: Route) { route = value; state = NavState() }
    fun fresh(nowMs: Long): Boolean = lastFix?.let { nowMs - it.elapsedMs in 0..15_000 } == true
    fun accept(fix: Fix, nowMs: Long): List<NavEvent>? {
        if (!fix.point.lat.isFinite() || !fix.point.lng.isFinite() ||
            fix.point.lat !in -90.0..90.0 || fix.point.lng !in -180.0..180.0 ||
            !fix.accuracyM.isFinite() || fix.accuracyM !in 0.0..50.0 ||
            nowMs - fix.elapsedMs !in 0..15_000 ||
            lastFix?.let { fix.elapsedMs <= it.elapsedMs } == true) return null
        lastFix = fix
        val speed = fix.speedMps?.takeIf { it.isFinite() && it >= 0.0 }
        if (speed != null && speed >= 2.0 && fix.bearingDeg?.isFinite() == true) {
            heading = ((fix.bearingDeg % 360.0) + 360.0) % 360.0
        }
        val current = route ?: return emptyList()
        val corridor = NavEngine.offRouteCorridor(TravelMode.DRIVE, fix.accuracyM)
        val result = NavEngine.update(current, state, fix.point, imperial = true,
            speedMps = speed ?: 0.0, bearingDeg = if ((speed ?: 0.0) >= 2.0) fix.bearingDeg else null,
            offRouteM = corridor, farOffM = NavEngine.farOffDistance(TravelMode.DRIVE, corridor))
        state = result.first
        // Speak/Haptic events are deliberately never connected to an output device.
        return result.second.filter { it is NavEvent.RerouteNeeded || it is NavEvent.Arrived }
    }
}
