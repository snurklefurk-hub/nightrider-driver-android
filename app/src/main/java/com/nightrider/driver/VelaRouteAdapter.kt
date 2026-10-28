package com.nightrider.driver

import app.vela.core.nav.OsrmManeuvers
import app.vela.core.model.*
import org.json.JSONArray
import org.json.JSONObject

/** Convert OSRM's full geometry and steps to Vela's unmodified route model. */
object VelaRouteAdapter {
    fun point(a: JSONArray): LatLng {
        val lng = a.getDouble(0); val lat = a.getDouble(1)
        require(lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0)
        return LatLng(lat, lng)
    }
    fun parse(json: JSONObject): Route {
        require(json.optString("code") == "Ok") { "Routing service could not find a route" }
        val r = json.getJSONArray("routes").getJSONObject(0)
        val coords = r.getJSONObject("geometry").getJSONArray("coordinates")
        val poly = (0 until coords.length()).map { point(coords.getJSONArray(it)) }
        require(poly.size >= 2)
        val legs = r.getJSONArray("legs")
        val converted = (0 until legs.length()).map { index ->
            val leg = legs.getJSONObject(index)
            val steps = leg.getJSONArray("steps")
            val maneuvers = (0 until steps.length()).map { i ->
                val step = steps.getJSONObject(i)
                val m = step.getJSONObject("maneuver")
                val type = m.getString("type")
                val mod = m.optString("modifier").ifBlank { null }
                val road = step.optString("name").ifBlank { null }
                val intersections = step.optJSONArray("intersections")
                val laneArray = intersections?.optJSONObject(0)?.optJSONArray("lanes")
                val lanes = if (laneArray == null) emptyList() else (0 until laneArray.length()).map { j ->
                    val lane = laneArray.getJSONObject(j); val indications = lane.getJSONArray("indications")
                    Lane((0 until indications.length()).map { indications.getString(it) }, lane.optBoolean("valid"))
                }
                Maneuver(OsrmManeuvers.osrmType(type, mod),
                    OsrmManeuvers.osrmPhrase(type, mod, road, step.optString("destinations").ifBlank { null },
                        step.optString("exits").ifBlank { null }, m.optInt("exit").takeIf { it > 0 }),
                    point(m.getJSONArray("location")), step.getDouble("distance"), step.getDouble("duration"),
                    road = road, ref = step.optString("ref").ifBlank { null }, lanes = lanes,
                    roundaboutExit = m.optInt("exit").takeIf { it > 0 })
            }
            require(maneuvers.isNotEmpty())
            RouteLeg(leg.getDouble("distance"), leg.getDouble("duration"), null, maneuvers)
        }
        val distance = r.getDouble("distance"); val duration = r.getDouble("duration")
        require(distance.isFinite() && duration.isFinite() && distance >= 0 && duration >= 0)
        return Route(poly, converted, distance, duration, null, summary = "OSRM · no live traffic",
            source = RouteSource.OSRM)
    }
}
