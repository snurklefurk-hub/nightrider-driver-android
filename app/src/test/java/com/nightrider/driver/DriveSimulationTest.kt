package com.nightrider.driver

import org.junit.Assert.*
import org.junit.Test

class DriveSimulationTest {
    private fun simulation() = DriveSimulation(
        SimulationLeg(listOf(SimulationPoint(32.08, -81.09), SimulationPoint(32.081, -81.09))),
        SimulationLeg(listOf(SimulationPoint(32.081, -81.09), SimulationPoint(32.083, -81.09)))
    )
    @Test fun interpolationUsesDistanceAndHandlesRepeatedPoints() {
        val a = SimulationPoint(32.0, -81.0); val b = SimulationPoint(32.002, -81.0)
        val leg = SimulationLeg(listOf(a, a, b))
        assertEquals(a, leg.pointAt(-5.0))
        assertEquals(32.001, leg.pointAt(leg.lengthMeters / 2).lat, 0.000001)
        assertEquals(b, leg.pointAt(Double.MAX_VALUE))
    }
    @Test fun playbackPausesAtPickupAndDestination() {
        val sim = simulation(); sim.play(); sim.advance(1000.0)
        assertEquals(SimulationStage.WAITING, sim.stage); assertFalse(sim.playing)
        assertEquals(sim.approach.points.last(), sim.position)
        sim.play(); sim.advance(1000.0)
        assertEquals(SimulationStage.ARRIVED, sim.stage); assertFalse(sim.playing)
        assertEquals(sim.trip.points.last(), sim.position)
    }
    @Test fun speedAndPauseUseElapsedTime() {
        val slow = simulation(); val fast = simulation()
        slow.play(); fast.play(); fast.speedMultiplier = 4.0
        slow.advance(1.0); fast.advance(1.0)
        assertEquals(slow.distanceMeters * 4, fast.distanceMeters, 0.00001)
        fast.pause(); val distance = fast.distanceMeters; fast.advance(10.0)
        assertEquals(distance, fast.distanceMeters, 0.0)
    }
    @Test fun gpsFailuresDoNotAlterProgressOrRecordedRoute() {
        val sim = simulation(); sim.seek(0.4); val normal = sim.position
        sim.gps = SimulationGps.LOST; assertNull(sim.position)
        sim.gps = SimulationGps.OFF_ROUTE
        assertEquals(normal!!.lat + 0.0012, sim.position!!.lat, 0.0000001)
        assertEquals(0.4, sim.progress, 0.000001)
        sim.gps = SimulationGps.NORMAL; assertEquals(normal, sim.position)
        sim.reset(); assertEquals(SimulationStage.ASSIGNED, sim.stage); assertEquals(0.0, sim.progress, 0.0)
    }
    @Test fun stageSwitchesChooseCorrectLegAndClampScrubbing() {
        val sim = simulation(); sim.setStage(SimulationStage.TRIP); sim.seek(2.0)
        assertEquals(sim.trip.points.last(), sim.position)
        sim.setStage(SimulationStage.PICKUP); sim.seek(-1.0)
        assertEquals(sim.approach.points.first(), sim.position)
    }
    @Test fun scrubbingBeforePlaybackKeepsTheSelectedPosition() {
        val sim = simulation(); sim.seek(0.5)
        val selected = sim.position
        assertEquals(SimulationStage.PICKUP, sim.stage)
        sim.play(); assertEquals(selected, sim.position)
    }
}
