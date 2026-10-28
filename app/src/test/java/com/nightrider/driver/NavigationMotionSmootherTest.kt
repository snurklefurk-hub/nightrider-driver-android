package com.nightrider.driver

import org.junit.Assert.*
import org.junit.Test

class NavigationMotionSmootherTest {
    @Test fun highwayFixesMoveGraduallyInsteadOfTeleporting() {
        val motion = NavigationMotionSmoother()
        val a = SimulationPoint(32.1700, -81.3900)
        val b = SimulationPoint(32.1700, -81.3888)
        motion.acceptFix(a, 90.0, 0L)
        assertEquals(a, motion.frame(0L)!!.position)
        motion.acceptFix(b, 90.0, 5_000L)
        assertEquals(a, motion.frame(5_000L)!!.position)
        val halfway = motion.frame(6_750L)!!
        assertEquals(0.5, halfway.progress, 0.001)
        assertEquals((a.lon + b.lon) / 2, halfway.position.lon, 0.00001)
        assertEquals(b, motion.frame(8_500L)!!.position)
        // No speculative movement after the latest measured GPS fix.
        assertEquals(b, motion.frame(13_500L)!!.position)
    }

    @Test fun consecutiveNewFixesContinueFromCurrentVisualPosition() {
        val motion = NavigationMotionSmoother()
        val a = SimulationPoint(32.0, -81.0)
        val b = SimulationPoint(32.001, -81.0)
        val c = SimulationPoint(32.002, -81.0)
        motion.acceptFix(a, 0.0, 1_000L)
        motion.acceptFix(b, 0.0, 6_000L)
        val before = motion.frame(7_000L)!!.position
        motion.acceptFix(c, 0.0, 7_000L)
        val after = motion.frame(7_000L)!!.position
        assertEquals(before.lat, after.lat, 0.0000001)
        assertEquals(c, motion.frame(10_600L)!!.position)
    }

    @Test fun headingInterpolatesAcrossNorthWithoutSpinningBackwards() {
        val motion = NavigationMotionSmoother()
        val a = SimulationPoint(32.0, -81.0)
        motion.acceptFix(a, 350.0, 0L)
        motion.acceptFix(SimulationPoint(32.001, -81.0), 10.0, 5_000L)
        val mid = motion.frame(6_750L)!!
        assertTrue(mid.bearingDegrees!! < 2.0 || mid.bearingDegrees!! > 358.0)
        assertEquals(10.0, motion.frame(8_500L)!!.bearingDegrees!!, 0.001)
    }

    @Test fun pauseRecoverySnapsRatherThanAnimatingMilesOfUnverifiedTravel() {
        val motion = NavigationMotionSmoother()
        val a = SimulationPoint(32.0, -81.0)
        val b = SimulationPoint(32.03, -81.02)
        motion.acceptFix(a, 0.0, 0L)
        motion.acceptFix(b, null, 21_000L)
        val frame = motion.frame(21_000L)!!
        assertEquals(b, frame.position)
        assertEquals(1.0, frame.progress, 0.001)
        motion.clear()
        assertNull(motion.frame(22_000L))
    }

    @Test fun stationaryFixDoesNotMoveMarker() {
        val motion = NavigationMotionSmoother()
        val a = SimulationPoint(32.0, -81.0)
        motion.acceptFix(a, null, 0L)
        motion.acceptFix(a, null, 5_000L)
        assertEquals(a, motion.frame(5_500L)!!.position)
        assertEquals(1.0, motion.frame(5_500L)!!.progress, 0.001)
    }
}
