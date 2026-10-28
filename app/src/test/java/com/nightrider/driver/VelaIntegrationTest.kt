package com.nightrider.driver

import app.vela.core.model.LatLng
import app.vela.core.nav.NavEvent
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VelaIntegrationTest {
    private fun fix(t: Long, speed: Double? = 0.0, bearing: Double? = 90.0, accuracy: Double = 10.0) =
        VelaDriveSession.Fix(LatLng(32.0,-81.0),t,accuracy,speed,bearing)

    @Test fun stationaryHeadingDoesNotFollowNoisyCourseAndStalePositionIsRetained() {
        val session = VelaDriveSession()
        assertNotNull(session.accept(fix(1000,10.0,90.0),1000))
        assertNotNull(session.accept(fix(2000,0.0,180.0),2000))
        assertEquals(90.0,session.heading,0.0)
        assertFalse(session.fresh(18001))
        assertNotNull(session.lastFix)
    }
    @Test fun coarseStaleAndOutOfOrderFixesCannotMoveNavigation() {
        val session = VelaDriveSession()
        session.accept(fix(2000),2000)
        assertNull(session.accept(fix(3000,accuracy=100.0),3000))
        assertNull(session.accept(fix(1000),3000))
        assertNull(session.accept(fix(4000),20001))
        assertEquals(2000L,session.lastFix!!.elapsedMs)
    }
    private fun ride(stage: String, driver: String = "Driver", id: String = "one") = JSONObject()
        .put("id",id).put("status",stage).put("driver",driver)
        .put("pickup_coords",JSONArray("[32.0,-81.0]"))
        .put("dropoff_coords",JSONArray("[33.0,-82.0]"))
    @Test fun realRideStagesSelectPickupThenDropoffWithoutMutatingTheRide() {
        for (stage in listOf("accepted","to_pickup","at_pickup")) {
            val input = ride(stage); val before = input.toString()
            val target = VelaRideTarget.select(JSONArray().put(input),"Driver")!!
            assertEquals(32.0,target.point.lat,0.0); assertEquals(before,input.toString())
        }
        for (stage in listOf("to_dropoff","at_dropoff")) {
            assertEquals(33.0,VelaRideTarget.select(JSONArray().put(ride(stage)),"Driver")!!.point.lat,0.0)
        }
        assertNull(VelaRideTarget.select(JSONArray().put(ride("completed")),"Driver"))
        assertNull(VelaRideTarget.select(JSONArray().put(ride("accepted","Other")),"Driver"))
    }
    @Test fun activeTripWinsAndCoordinateChangesInvalidateTheRoute() {
        val rides = JSONArray().put(ride("accepted",id="new")).put(ride("to_dropoff",id="current"))
        assertTrue(VelaRideTarget.select(rides,"Driver")!!.key.startsWith("current|"))
        val r=ride("accepted");val key=VelaRideTarget.select(JSONArray().put(r),"Driver")!!.key
        r.put("pickup_coords",JSONArray("[32.1,-81.0]"))
        assertNotEquals(key,VelaRideTarget.select(JSONArray().put(r),"Driver")!!.key)
    }
    @Test fun osrmFullStepsBecomeVelaManeuversAndGuidanceStaysSilent() {
        val response=JSONObject("""{"code":"Ok","routes":[{"distance":100,"duration":12,"geometry":{"coordinates":[[-81,32],[-81,32.001]]},"legs":[{"distance":100,"duration":12,"steps":[{"distance":100,"duration":12,"name":"Main Street","maneuver":{"type":"depart","modifier":"straight","location":[-81,32]}},{"distance":0,"duration":0,"name":"Main Street","maneuver":{"type":"arrive","location":[-81,32.001]}}]}]}]}""")
        val route=VelaRouteAdapter.parse(response)
        assertEquals(2,route.maneuvers.size)
        assertTrue(route.maneuvers.first().instruction.contains("Main Street"))
        val session=VelaDriveSession();session.adopt(route)
        val events=session.accept(fix(1000),1000)!!
        assertTrue(events.none { it is NavEvent.Speak || it is NavEvent.Haptic })
    }

    @Test fun velaInstructionRulesPreserveUTurnsArrivalSideAndRoadRenames() {
        val text = app.vela.core.nav.OsrmManeuvers
        assertTrue(text.osrmPhrase("continue","uturn","Main Street",null,null,null).lowercase().contains("u-turn"))
        assertTrue(text.osrmPhrase("arrive","left",null,null,null,null).lowercase().contains("left"))
        assertEquals(app.vela.core.model.ManeuverType.CONTINUE, text.osrmType("new name","slight left"))
        assertEquals(app.vela.core.model.ManeuverType.TURN_LEFT, text.osrmType("continue","left"))
    }
}
