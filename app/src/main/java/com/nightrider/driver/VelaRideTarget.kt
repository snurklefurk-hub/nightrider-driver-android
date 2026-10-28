package com.nightrider.driver

import app.vela.core.model.LatLng
import org.json.JSONArray

/** NightRider's existing ride stages select the navigation leg; navigation cannot change them. */
object VelaRideTarget {
    data class Target(val point: LatLng, val label: String, val key: String)
    fun select(rides: JSONArray, driver: String): Target? {
        val active = (0 until rides.length()).map { rides.getJSONObject(it) }.filter {
            it.optString("driver") == driver && it.optString("status") in
                setOf("accepted", "to_pickup", "at_pickup", "to_dropoff", "at_dropoff")
        }
        val ride = active.firstOrNull { it.optString("status") in setOf("to_dropoff", "at_dropoff") }
            ?: active.firstOrNull() ?: return null
        val trip = ride.optString("status") in setOf("to_dropoff", "at_dropoff")
        val coords = ride.optJSONArray(if (trip) "dropoff_coords" else "pickup_coords") ?: return null
        if (coords.length() != 2) return null
        val lat = coords.optDouble(0, Double.NaN); val lng = coords.optDouble(1, Double.NaN)
        if (!lat.isFinite() || !lng.isFinite() || lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return Target(LatLng(lat,lng), if (trip) "To drop-off" else "To pickup",
            "${ride.optString("id")}|$trip|$lat|$lng")
    }
}
